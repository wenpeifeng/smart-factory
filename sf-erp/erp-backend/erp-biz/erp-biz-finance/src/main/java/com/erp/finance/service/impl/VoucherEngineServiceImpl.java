package com.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.finance.context.BookContext;
import com.erp.finance.dto.vo.EngineDocDetailVO;
import com.erp.finance.dto.vo.EngineDocHeaderVO;
import com.erp.finance.dto.vo.EngineScanCandidateVO;
import com.erp.finance.dto.vo.VoucherEntryVO;
import com.erp.finance.dto.vo.VoucherPreviewVO;
import com.erp.finance.entity.FinAccount;
import com.erp.finance.entity.FinSubject;
import com.erp.finance.entity.FinVoucher;
import com.erp.finance.entity.FinVoucherEntry;
import com.erp.finance.entity.FinVoucherTemplate;
import com.erp.finance.mapper.FinAccountMapper;
import com.erp.finance.mapper.FinSubjectMapper;
import com.erp.finance.mapper.FinVoucherEntryMapper;
import com.erp.finance.mapper.FinVoucherMapper;
import com.erp.finance.mapper.FinVoucherTemplateMapper;
import com.erp.finance.mapper.VoucherEngineMapper;
import com.erp.finance.service.FinPeriodService;
import com.erp.finance.service.VoucherEngineService;
import com.erp.finance.support.FinanceBizNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 凭证模板引擎实现（V521）。
 * <p>
 * 生成流程：读单据主子表（跨模块只读约定表）→ 按模板规则组装凭证（DRAFT）→
 * 摘要变量替换（{单号}/{日期}/{类型}）→ 金额 BigDecimal HALF_UP 2 位 →
 * 同科目+同方向分录合并 → 剔除 0 金额腿 → 借贷平衡校验（|差|&gt;0.01 抛异常不落库，
 * 0&lt;|差|≤0.01 尾差调整到金额最大的一腿）→ 幂等落库（uk_voucher_source 兜底）。
 * <p>
 * 红冲联动：销售退货/采购退货为红字单据，金额取负（负数分录）；
 * 发票红字票金额列自带负号，不再取负。
 * 多账套：凭证落 BookContext 当前账套（扫描器默认 1001 主账套，可配置）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherEngineServiceImpl implements VoucherEngineService {

    // ==================== 业务类型常量（八类） ====================
    /** 采购入库 */
    public static final String PUR_INSTOCK = "PUR_INSTOCK";
    /** 销售出库 */
    public static final String SAL_OUTSTOCK = "SAL_OUTSTOCK";
    /** 销售退货 */
    public static final String SAL_RET = "SAL_RET";
    /** 采购退货 */
    public static final String PUR_RET = "PUR_RET";
    /** 销项发票 */
    public static final String INVOICE_SALES = "INVOICE_SALES";
    /** 进项发票 */
    public static final String INVOICE_PURCHASE = "INVOICE_PURCHASE";
    /** 收款单 */
    public static final String RECEIPT = "RECEIPT";
    /** 付款单 */
    public static final String PAYMENT = "PAYMENT";

    /** 八类业务类型 → 中文名（摘要变量 {类型} / 备注用） */
    private static final Map<String, String> BIZ_TYPE_NAMES = Map.of(
            PUR_INSTOCK, "采购入库",
            SAL_OUTSTOCK, "销售出库",
            SAL_RET, "销售退货",
            PUR_RET, "采购退货",
            INVOICE_SALES, "销项发票",
            INVOICE_PURCHASE, "进项发票",
            RECEIPT, "收款",
            PAYMENT, "付款");

    /** 红字单据（金额取负）：两类业务退货；发票红字票金额列自带负号不在此列 */
    private static final Set<String> RED_SIGN_TYPES = Set.of(SAL_RET, PUR_RET);

    /** 凭证状态：草稿 */
    private static final String STATUS_DRAFT = "DRAFT";

    /** 尾差容差：|借−贷| ≤ 0.01 视为可调平（尾差调整），超出抛异常 */
    private static final BigDecimal BALANCE_TOLERANCE = new BigDecimal("0.01");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final VoucherEngineMapper engineMapper;
    private final FinVoucherTemplateMapper templateMapper;
    private final FinVoucherMapper voucherMapper;
    private final FinVoucherEntryMapper entryMapper;
    private final FinSubjectMapper subjectMapper;
    private final FinAccountMapper accountMapper;
    private final FinPeriodService periodService;
    private final FinanceBizNoGenerator bizNoGenerator;
    private final TransactionTemplate transactionTemplate;

    // ==================== 生成 ====================

    @Override
    public GenerationResult generate(String bizType, Long bizId) {
        requireBizType(bizType);
        FinVoucherTemplate template = requireEnabledTemplate(bizType);
        Long bookId = BookContext.getBookId();

        // 幂等第一道：已生成直接返回（应用层）
        FinVoucher existing = findExisting(bizType, bizId, bookId);
        if (existing != null) {
            return new GenerationResult(existing.getId(), existing.getVoucherNo(), false,
                    "该单据已生成过凭证（幂等跳过）");
        }

        AssembledVoucher assembled = assemble(template, bizType, bizId);
        // 期间控制：已结账期间不再生成（单据期间凭证由人工决定是否补记）
        periodService.assertPeriodOpen(assembled.voucherDate(), bookId, "生成自动凭证");

        // 落库（事务内二次幂等检查 + uk_voucher_source 唯一键兜底并发）
        FinVoucher created = transactionTemplate.execute(tx -> {
            FinVoucher again = findExisting(bizType, bizId, bookId);
            if (again != null) {
                return null;
            }
            FinVoucher voucher = new FinVoucher();
            voucher.setBookId(bookId);
            voucher.setVoucherNo(bizNoGenerator.next(FinanceBizNoGenerator.VOUCHER));
            voucher.setVoucherDate(assembled.voucherDate());
            voucher.setWord(assembled.word());
            voucher.setAttachmentCount(0);
            voucher.setTotalDebit(assembled.totalDebit());
            voucher.setTotalCredit(assembled.totalCredit());
            voucher.setStatus(STATUS_DRAFT);
            voucher.setRemark("自动生成：%s %s".formatted(BIZ_TYPE_NAMES.get(bizType), assembled.docNo()));
            voucher.setSourceType(bizType);
            voucher.setSourceId(bizId);
            try {
                voucherMapper.insert(voucher);
            } catch (DuplicateKeyException e) {
                // uk_voucher_source 冲突两种情况：
                // 1) 并发生成：他人已插入有效凭证 → 让位返回已有凭证；
                // 2) 软删旧凭证仍占用唯一键（MP 逻辑删行对 uk 仍可见）：
                //    释放其 source 列后重试一次，使"删凭证后重扫重新生成"可行。
                Long staleId = engineMapper.selectAnyVoucherIdBySource(bookId, bizType, bizId);
                boolean released = staleId != null
                        && engineMapper.clearSourceOfDeletedVoucher(staleId) > 0;
                if (released) {
                    voucherMapper.insert(voucher);
                    log.warn("[凭证引擎][幂等键] 释放软删凭证[{}]占用的来源键 {}/{} 后重新生成",
                            staleId, bizType, bizId);
                } else {
                    FinVoucher winner = findExisting(bizType, bizId, bookId);
                    if (winner != null) {
                        return null;
                    }
                    throw e;
                }
            }
            int lineNo = 1;
            for (AssembledEntry entry : assembled.entries()) {
                FinVoucherEntry ve = new FinVoucherEntry();
                ve.setBookId(bookId);
                ve.setVoucherId(voucher.getId());
                ve.setLineNo(lineNo++);
                ve.setSummary(entry.summary());
                ve.setSubjectId(entry.subject().getId());
                ve.setDebitAmount("DEBIT".equals(entry.side()) ? entry.amount() : BigDecimal.ZERO);
                ve.setCreditAmount("CREDIT".equals(entry.side()) ? entry.amount() : BigDecimal.ZERO);
                entryMapper.insert(ve);
            }
            return voucher;
        });
        if (created == null) {
            FinVoucher winner = findExisting(bizType, bizId, bookId);
            return new GenerationResult(winner == null ? null : winner.getId(),
                    winner == null ? null : winner.getVoucherNo(), false, "该单据已生成过凭证（并发幂等跳过）");
        }
        // 审计日志：自动凭证来源与金额必须可检索
        log.info("[凭证引擎][生成] 来源={}/{} 单号={} 凭证号={} 红字={} 分录数={} 借方合计={} 贷方合计={}",
                bizType, bizId, assembled.docNo(), created.getVoucherNo(), assembled.red(),
                assembled.entries().size(),
                created.getTotalDebit().toPlainString(), created.getTotalCredit().toPlainString());
        return new GenerationResult(created.getId(), created.getVoucherNo(), true, null);
    }

    // ==================== 试算预览 ====================

    @Override
    public VoucherPreviewVO preview(String bizType, Long bizId) {
        requireBizType(bizType);
        FinVoucherTemplate template = templateMapper.selectOne(
                Wrappers.lambdaQuery(FinVoucherTemplate.class)
                        .eq(FinVoucherTemplate::getBizType, bizType)
                        .last("LIMIT 1"));
        if (template == null) {
            return new VoucherPreviewVO(bizType, bizId, null, null, null, false,
                    VoucherPreviewVO.NO_TEMPLATE, null, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of(), "该业务类型未配置凭证模板");
        }
        if (template.getEnabled() == null || template.getEnabled() != 1) {
            // 停用模板：返回将生成的内容仍按规则组装（便于排查），状态标记停用
            AssembledVoucher assembled = assemble(template, bizType, bizId);
            return toPreview(bizType, bizId, assembled, VoucherPreviewVO.TEMPLATE_DISABLED,
                    "模板[%s]已停用，扫描器不再按该模板生成凭证".formatted(template.getName()));
        }
        FinVoucher existing = findExisting(bizType, bizId, BookContext.getBookId());
        if (existing != null) {
            AssembledVoucher assembled = assemble(template, bizType, bizId);
            return new VoucherPreviewVO(bizType, bizId, assembled.docNo(), assembled.voucherDate(),
                    assembled.word(), assembled.red(), VoucherPreviewVO.ALREADY_GENERATED,
                    existing.getId(), existing.getVoucherNo(),
                    assembled.totalDebit(), assembled.totalCredit(),
                    toEntryVOs(assembled), "该单据已生成过凭证：[%s]（幂等跳过）".formatted(existing.getVoucherNo()));
        }
        AssembledVoucher assembled = assemble(template, bizType, bizId);
        return toPreview(bizType, bizId, assembled, VoucherPreviewVO.WILL_GENERATE, "预览不落库，与实际生成同构");
    }

    // ==================== 扫描 ====================

    @Override
    public int scan(String bizType, LocalDate from, LocalDate to, Integer daysBack) {
        if (bizType != null && !bizType.isBlank()) {
            requireBizType(bizType.trim());
        }
        Long bookId = BookContext.getBookId();
        List<EngineScanCandidateVO> candidates = engineMapper.selectScanCandidates(
                bizType == null || bizType.isBlank() ? null : bizType.trim(),
                from, to, daysBack, bookId);
        int created = 0;
        int failed = 0;
        for (EngineScanCandidateVO c : candidates) {
            try {
                GenerationResult result = generate(c.bizType(), c.id());
                if (result.created()) {
                    created++;
                }
            } catch (Exception e) {
                // 单张失败不中断本轮（模板停用/期间已结账/金额不平等），日志留痕下轮重试
                failed++;
                log.warn("[凭证引擎][扫描] 生成失败 bizType={} bizId={} 单号={} 原因={}:{}",
                        c.bizType(), c.id(), c.docNo(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
        if (created > 0 || failed > 0) {
            log.info("[凭证引擎][扫描] 本轮候选={} 新生成={} 失败={} 账套={}",
                    candidates.size(), created, failed, bookId);
        }
        return created;
    }

    // ==================== 组装核心 ====================

    /**
     * 按模板规则组装凭证（不落库）：金额来源 → 科目解析 → 摘要替换 → 合并 → 平衡校验。
     */
    private AssembledVoucher assemble(FinVoucherTemplate template, String bizType, Long bizId) {
        EngineDocHeaderVO header = engineMapper.selectDocHeader(bizType, bizId);
        if (header == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.doc-not-exists", BIZ_TYPE_NAMES.get(bizType), bizId);
        }
        AmountContext amounts = buildAmounts(bizType, header);
        boolean red = RED_SIGN_TYPES.contains(bizType)
                || (header.redFlag() != null && header.redFlag() == 1);
        BigDecimal sign = red && RED_SIGN_TYPES.contains(bizType) ? BigDecimal.ONE.negate() : BigDecimal.ONE;

        List<EntryRule> rules = parseRules(template);
        Long bookId = BookContext.getBookId();
        // 同科目+方向合并：LinkedHashMap 保持模板规则顺序
        Map<String, AssembledEntry> merged = new LinkedHashMap<>();
        for (EntryRule rule : rules) {
            BigDecimal base = switch (rule.amountExpr() == null ? "" : rule.amountExpr().trim()) {
                case "order_total" -> amounts.orderTotal();
                case "detail_tax_excl_total" -> amounts.detailTaxExclTotal();
                case "tax_total" -> amounts.taxTotal();
                case "detail_cost_total" -> amounts.detailCostTotal();
                default -> throw BusinessException.ofKey("erp.fin.tpl-amount-source-unsupported", template.getName(), rule.amountExpr());
            };
            BigDecimal amount = base.multiply(sign).setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() == 0) {
                continue; // 0 金额腿剔除（如历史出库行无 actual_cost 的成本腿）
            }
            String side = rule.side() == null ? "" : rule.side().trim();
            if (!"DEBIT".equals(side) && !"CREDIT".equals(side)) {
                throw BusinessException.ofKey("erp.fin.tpl-side-invalid", template.getName(), rule.side());
            }
            FinSubject subject = resolveSubject(template, rule.subjectCode(), header, bookId);
            String summary = renderSummary(rule.summary(), header, bizType);
            String key = side + ":" + subject.getId();
            merged.compute(key, (k, v) -> {
                if (v == null) {
                    return new AssembledEntry(side, subject, summary, amount);
                }
                // 多明细按科目+方向合并
                return new AssembledEntry(v.side(), v.subject(), v.summary(),
                        v.amount().add(amount));
            });
        }
        List<AssembledEntry> entries = new ArrayList<>(merged.values());
        if (entries.size() < 2) {
            throw BusinessException.ofKey("erp.fin.tpl-effective-lines-insufficient", BIZ_TYPE_NAMES.get(bizType), header.docNo(), bizId, template.getName());
        }
        // 平衡校验与尾差调整
        BigDecimal totalDebit = entries.stream().filter(e -> "DEBIT".equals(e.side()))
                .map(AssembledEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = entries.stream().filter(e -> "CREDIT".equals(e.side()))
                .map(AssembledEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diff = totalDebit.subtract(totalCredit);
        if (diff.abs().compareTo(BALANCE_TOLERANCE) > 0) {
            log.warn("[凭证引擎][校验] 拒绝 原因=借贷不平衡 bizType={}/{} 借方合计={} 贷方合计={} 差额={}",
                    bizType, bizId, totalDebit.toPlainString(), totalCredit.toPlainString(),
                    diff.toPlainString());
            throw BusinessException.ofKey("erp.fin.tpl-unbalanced", totalDebit.toPlainString(), totalCredit.toPlainString(), diff.toPlainString());
        }
        if (diff.signum() != 0) {
            adjustTailDiff(entries, diff);
            totalDebit = entries.stream().filter(e -> "DEBIT".equals(e.side()))
                    .map(AssembledEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            totalCredit = entries.stream().filter(e -> "CREDIT".equals(e.side()))
                    .map(AssembledEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        String word = RECEIPT.equals(bizType) ? "收" : PAYMENT.equals(bizType) ? "付" : "转";
        return new AssembledVoucher(header.docNo(), header.docDate(), word, red,
                totalDebit.setScale(2, RoundingMode.HALF_UP), totalCredit.setScale(2, RoundingMode.HALF_UP), entries);
    }

    /**
     * 金额来源口径（对照各单据表明细汇总，见 V521 头部口径表）：
     * <ul>
     *   <li>采购入库/退货（price 为含税单价）：不含税=Σround(amount÷(1+税率%))，
     *       税额=价税合计−不含税合计（倒挤，保证 借1405+借222101=贷2202 必平）；</li>
     *   <li>销售出库/退货（price 为不含税单价）：order_total 即不含税合计；
     *       成本=Σactual_cost（退货按数量分摊原出库行 actual_cost）；</li>
     *   <li>发票：表内 amount/tax_amount/total_amount 直接取用（红字票自带负号）；</li>
     *   <li>收付款：单头 amount。</li>
     * </ul>
     */
    private AmountContext buildAmounts(String bizType, EngineDocHeaderVO header) {
        BigDecimal orderTotal = nz(header.totalAmount());
        switch (bizType) {
            case PUR_INSTOCK, PUR_RET -> {
                BigDecimal excl = BigDecimal.ZERO;
                for (EngineDocDetailVO d : engineMapper.selectDocDetails(bizType, header.id())) {
                    BigDecimal rate = nz(d.taxRate());
                    BigDecimal divisor = BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
                    excl = excl.add(nz(d.amount()).divide(divisor, 2, RoundingMode.HALF_UP));
                }
                return new AmountContext(orderTotal, excl, orderTotal.subtract(excl), BigDecimal.ZERO);
            }
            case SAL_OUTSTOCK -> {
                BigDecimal cost = BigDecimal.ZERO;
                for (EngineDocDetailVO d : engineMapper.selectDocDetails(bizType, header.id())) {
                    cost = cost.add(nz(d.actualCost()));
                }
                return new AmountContext(orderTotal, orderTotal, BigDecimal.ZERO, cost);
            }
            case SAL_RET -> {
                BigDecimal cost = BigDecimal.ZERO;
                for (EngineDocDetailVO d : engineMapper.selectDocDetails(bizType, header.id())) {
                    BigDecimal originQty = d.originQty();
                    BigDecimal originCost = d.originActualCost();
                    if (originQty == null || originCost == null || originQty.signum() <= 0) {
                        continue; // 原出库行无成本快照（历史行），计 0
                    }
                    cost = cost.add(originCost.multiply(nz(d.qty()))
                            .divide(originQty, 2, RoundingMode.HALF_UP));
                }
                return new AmountContext(orderTotal, orderTotal, BigDecimal.ZERO, cost);
            }
            case INVOICE_SALES, INVOICE_PURCHASE -> {
                return new AmountContext(orderTotal, nz(header.amountExcl()), nz(header.taxAmount()), BigDecimal.ZERO);
            }
            case RECEIPT, PAYMENT -> {
                return new AmountContext(orderTotal, orderTotal, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            default -> throw BusinessException.ofKey("erp.fin.biz-type-unsupported", bizType);
        }
    }

    /** 科目解析：@account → 按单据资金账户类型（CASH→1001 / BANK→1002） */
    private FinSubject resolveSubject(FinVoucherTemplate template, String subjectCode,
                                      EngineDocHeaderVO header, Long bookId) {
        String code = subjectCode == null ? "" : subjectCode.trim();
        if ("@account".equals(code)) {
            if (header.accountId() == null) {
                throw BusinessException.ofKey("erp.fin.tpl-account-doc-no-fund-account", template.getName(), header.docNo());
            }
            FinAccount account = accountMapper.selectById(header.accountId());
            if (account == null) {
                throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.fund-account-not-exists", header.docNo(), header.accountId());
            }
            code = "CASH".equals(account.getType()) ? "1001" : "BANK".equals(account.getType()) ? "1002" : null;
            if (code == null) {
                throw BusinessException.ofKey("erp.fin.fund-account-type-unmappable", header.docNo(), account.getType());
            }
        }
        if (code.isEmpty()) {
            throw BusinessException.ofKey("erp.fin.tpl-line-subject-code-empty", template.getName());
        }
        FinSubject subject = subjectMapper.selectOne(Wrappers.lambdaQuery(FinSubject.class)
                .eq(FinSubject::getBookId, bookId)
                .eq(FinSubject::getCode, code)
                .last("LIMIT 1"));
        if (subject == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.tpl-subject-not-in-book", template.getName(), code, bookId);
        }
        if (subject.getStatus() == null || subject.getStatus() != 1) {
            throw BusinessException.ofKey("erp.fin.tpl-subject-disabled", template.getName(), subject.getCode(), subject.getName());
        }
        Long children = subjectMapper.selectCount(Wrappers.lambdaQuery(FinSubject.class)
                .eq(FinSubject::getBookId, bookId)
                .eq(FinSubject::getParentId, subject.getId()));
        if (children > 0) {
            throw BusinessException.ofKey("erp.fin.tpl-subject-not-leaf", template.getName(), subject.getCode(), subject.getName());
        }
        return subject;
    }

    /** 摘要变量替换：{单号} {日期} {类型} */
    private String renderSummary(String summaryTemplate, EngineDocHeaderVO header, String bizType) {
        String s = summaryTemplate == null || summaryTemplate.isBlank()
                ? "{类型} {单号}" : summaryTemplate;
        return s.replace("{单号}", header.docNo() == null ? "" : header.docNo())
                .replace("{日期}", header.docDate() == null ? "" : header.docDate().toString())
                .replace("{类型}", BIZ_TYPE_NAMES.getOrDefault(bizType, bizType));
    }

    /** 尾差调整（0 < |差| ≤ 0.01）：加到金额（绝对值）最大且差值方向的对面一腿 */
    private void adjustTailDiff(List<AssembledEntry> entries, BigDecimal diff) {
        String targetSide = diff.signum() > 0 ? "CREDIT" : "DEBIT";
        AssembledEntry target = null;
        for (AssembledEntry e : entries) {
            if (!targetSide.equals(e.side())) {
                continue;
            }
            if (target == null || e.amount().abs().compareTo(target.amount().abs()) > 0) {
                target = e;
            }
        }
        if (target == null) {
            throw BusinessException.ofKey("erp.fin.tail-diff-unadjustable", diff.toPlainString(), targetSide);
        }
        int idx = entries.indexOf(target);
        entries.set(idx, new AssembledEntry(target.side(), target.subject(), target.summary(),
                target.amount().add(diff.abs())));
        log.warn("[凭证引擎][尾差] 借贷差 {} 已调整到 {} 方向最大腿（科目 {}）",
                diff.toPlainString(), targetSide, target.subject().getCode());
    }

    // ==================== 工具 ====================

    private FinVoucherTemplate requireEnabledTemplate(String bizType) {
        FinVoucherTemplate template = templateMapper.selectOne(
                Wrappers.lambdaQuery(FinVoucherTemplate.class)
                        .eq(FinVoucherTemplate::getBizType, bizType)
                        .last("LIMIT 1"));
        if (template == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.biz-type-no-template", bizType);
        }
        if (template.getEnabled() == null || template.getEnabled() != 1) {
            throw BusinessException.ofKey("erp.fin.biz-type-template-disabled", bizType, template.getName());
        }
        return template;
    }

    private FinVoucher findExisting(String bizType, Long bizId, Long bookId) {
        return voucherMapper.selectOne(Wrappers.lambdaQuery(FinVoucher.class)
                .eq(FinVoucher::getBookId, bookId)
                .eq(FinVoucher::getSourceType, bizType)
                .eq(FinVoucher::getSourceId, bizId)
                .last("LIMIT 1"));
    }

    private List<EntryRule> parseRules(FinVoucherTemplate template) {
        try {
            List<EntryRule> rules = JSON.readValue(template.getEntryRules(),
                    new TypeReference<List<EntryRule>>() {
                    });
            if (rules == null || rules.isEmpty()) {
                throw BusinessException.ofKey("erp.fin.tpl-rule-empty", template.getName());
            }
            return rules;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.ofKey("erp.fin.tpl-rule-json-error", template.getName(), e.getMessage());
        }
    }

    private VoucherPreviewVO toPreview(String bizType, Long bizId, AssembledVoucher assembled,
                                       String status, String message) {
        return new VoucherPreviewVO(bizType, bizId, assembled.docNo(), assembled.voucherDate(),
                assembled.word(), assembled.red(), status, null, null,
                assembled.totalDebit(), assembled.totalCredit(), toEntryVOs(assembled), message);
    }

    private static List<VoucherEntryVO> toEntryVOs(AssembledVoucher assembled) {
        List<VoucherEntryVO> vos = new ArrayList<>();
        int lineNo = 1;
        for (AssembledEntry e : assembled.entries()) {
            vos.add(new VoucherEntryVO(lineNo++, e.summary(), e.subject().getId(),
                    e.subject().getCode(), e.subject().getName(),
                    "DEBIT".equals(e.side()) ? e.amount() : BigDecimal.ZERO,
                    "CREDIT".equals(e.side()) ? e.amount() : BigDecimal.ZERO));
        }
        return vos;
    }

    private static void requireBizType(String bizType) {
        if (bizType == null || !BIZ_TYPE_NAMES.containsKey(bizType)) {
            throw BusinessException.ofKey("erp.fin.biz-type-invalid2", bizType, String.join("/", BIZ_TYPE_NAMES.keySet()));
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // ==================== 内部结构 ====================

    /** 模板分录规则（fin_voucher_template.entry_rules JSON 数组元素） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EntryRule(String side, String subjectCode, String amountExpr, String summary) {
    }

    /** 金额来源上下文（四种口径，见 buildAmounts） */
    private record AmountContext(BigDecimal orderTotal, BigDecimal detailTaxExclTotal,
                                 BigDecimal taxTotal, BigDecimal detailCostTotal) {
    }

    /** 组装后的分录腿 */
    private record AssembledEntry(String side, FinSubject subject, String summary, BigDecimal amount) {
    }

    /** 组装完成的凭证（未落库） */
    private record AssembledVoucher(String docNo, LocalDate voucherDate, String word, boolean red,
                                    BigDecimal totalDebit, BigDecimal totalCredit,
                                    List<AssembledEntry> entries) {
    }
}
