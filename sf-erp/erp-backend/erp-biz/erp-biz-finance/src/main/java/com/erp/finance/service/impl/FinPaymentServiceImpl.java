package com.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.budget.dto.form.BudgetUsageRecordForm;
import com.erp.budget.dto.vo.BudgetCheckResult;
import com.erp.budget.service.BudgetControlService;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.finance.dto.form.PaymentForm;
import com.erp.finance.dto.request.PaymentQuery;
import com.erp.finance.dto.vo.PaymentDetailVO;
import com.erp.finance.dto.vo.PaymentItemVO;
import com.erp.finance.dto.vo.PaymentVO;
import com.erp.finance.context.BookContext;
import com.erp.finance.entity.FinAccount;
import com.erp.finance.entity.FinAccountFlow;
import com.erp.finance.entity.FinPayable;
import com.erp.finance.entity.FinPayment;
import com.erp.finance.entity.FinPaymentItem;
import com.erp.finance.mapper.BudgetIntegrationMapper;
import com.erp.finance.mapper.FinAccountFlowMapper;
import com.erp.finance.mapper.FinAccountMapper;
import com.erp.finance.mapper.FinPaymentItemMapper;
import com.erp.finance.mapper.FinPaymentMapper;
import com.erp.finance.mapper.FinPayableMapper;
import com.erp.finance.service.FinPaymentService;
import com.erp.finance.support.FinanceBizNoGenerator;
import com.erp.finance.support.PartnerNameLookup;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 付款单服务实现 —— 付款核销应付，与收款单完全对称。
 * <p>
 * 差异点：账户余额走减少方向的条件 UPDATE，余额不足（不允许透支）时
 * 影响 0 行 → 抛业务异常整体回滚。
 * <p>
 * 预算控制接线（finance → budget，依赖方向单向，见 docs/modules/budget.md §5）：
 * <ul>
 *   <li><b>创建付款单（核销执行入口）先校验</b>：付款归属 部门×费用科目×期间
 *       可定位时（表单显式指定 deptId+subjectId，或按「供应商→最近采购/应付单
 *       创建人部门」推导出部门且表单带科目）调用
 *       {@link BudgetControlService#check}——BLOCK 超预算抛异常整体拒绝；
 *       WARN 超预算在 remark 追加「⚠超预算xx%」提示并 log.warn 放行；
 *       部门/科目任一定位不到则跳过校验（兼容老数据）；</li>
 *   <li><b>付款落库成功后同事务登记执行数</b>：调用
 *       {@link BudgetControlService#record}（sourceType=PAYMENT，带付款单 id/单号），
 *       预算执行分析与占用校验立即可见。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinPaymentServiceImpl implements FinPaymentService {

    /** fin_payment.remark 列宽（VARCHAR(200)），WARN 提示追加后超长截断 */
    private static final int REMARK_MAX_LEN = 200;

    private final FinPaymentMapper paymentMapper;
    private final FinPaymentItemMapper paymentItemMapper;
    private final FinPayableMapper payableMapper;
    private final FinAccountMapper accountMapper;
    private final FinAccountFlowMapper flowMapper;
    private final PartnerNameLookup partnerNameLookup;
    private final FinanceBizNoGenerator bizNoGenerator;
    private final BudgetControlService budgetControlService;
    private final BudgetIntegrationMapper budgetIntegrationMapper;

    /** pur_order 表当前是否可用（false = 已确认缺失，跳过按采购单推导部门） */
    private volatile boolean purOrderTableAvailable = true;

    @Override
    public PageResult<PaymentVO> page(PaymentQuery query, long pageNum, long pageSize) {
        // MP 的 like(condition, column, val) 中 val 是即时求值：keyword 为 null 时不能在此处调 trim()
        String kw = query.keyword() == null ? "" : query.keyword().trim();
        var wrapper = Wrappers.lambdaQuery(FinPayment.class)
                .eq(FinPayment::getBookId, BookContext.getBookId())
                .eq(query.supplierId() != null, FinPayment::getSupplierId, query.supplierId())
                .like(!kw.isBlank(), FinPayment::getPaymentNo, kw)
                .orderByDesc(FinPayment::getId);
        PageResult<FinPayment> page = PageUtils.of(paymentMapper, wrapper, pageNum, pageSize);
        Map<Long, String> supplierNames = partnerNameLookup.supplierNames(
                page.getRecords().stream().map(FinPayment::getSupplierId).toList());
        Map<Long, String> accountNames = accountNames(
                page.getRecords().stream().map(FinPayment::getAccountId).toList());
        return page.map(p -> PaymentVO.from(p,
                supplierNames.get(p.getSupplierId()), accountNames.get(p.getAccountId())));
    }

    @Override
    public PaymentDetailVO detail(Long id) {
        FinPayment payment = requirePayment(id);
        List<FinPaymentItem> items = paymentItemMapper.selectList(
                Wrappers.lambdaQuery(FinPaymentItem.class)
                        .eq(FinPaymentItem::getPaymentId, id)
                        .orderByAsc(FinPaymentItem::getId));
        List<Long> payableIds = items.stream().map(FinPaymentItem::getPayableId).toList();
        Map<Long, FinPayable> payables = payableIds.isEmpty() ? Map.of()
                : payableMapper.selectBatchIds(payableIds).stream()
                        .collect(Collectors.toMap(FinPayable::getId, Function.identity()));

        List<PaymentItemVO> itemVOs = items.stream().map(item -> {
            FinPayable p = payables.get(item.getPayableId());
            return new PaymentItemVO(item.getPayableId(),
                    p == null ? null : p.getBizNo(),
                    p == null ? null : p.getAmount(),
                    p == null ? null : p.getSettledAmount(),
                    item.getVerifyAmount());
        }).toList();

        FinAccount account = accountMapper.selectById(payment.getAccountId());
        String supplierName = partnerNameLookup
                .supplierNames(List.of(payment.getSupplierId())).get(payment.getSupplierId());
        return new PaymentDetailVO(payment.getId(), payment.getPaymentNo(), payment.getSupplierId(),
                supplierName, payment.getAccountId(), account == null ? null : account.getName(),
                payment.getBizDate(), payment.getAmount(), payment.getVerifiedAmount(),
                payment.getRemark(), payment.getDeptId(), payment.getSubjectId(),
                payment.getCreateTime(), itemVOs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PaymentForm form) {
        BigDecimal amount = form.amount();
        Long bookId = BookContext.getBookId();
        // 审计日志：付款直接动账户余额，入口留痕供应商/账户/金额/核销笔数（操作人见 MDC userId）
        log.info("[付款核销][开始] 供应商id={} 账户id={} 金额={} 核销笔数={}",
                form.supplierId(), form.accountId(), amount.toPlainString(), form.items().size());
        // 1. 基础校验：金额一致性与账户可用性（账户必须属于当前账套）
        BigDecimal itemSum = BigDecimal.ZERO;
        for (PaymentForm.Item item : form.items()) {
            itemSum = itemSum.add(item.verifyAmount());
        }
        if (itemSum.compareTo(amount) != 0) {
            throw BusinessException.ofKey("erp.fin.pay-amount-mismatch", amount.toPlainString(), itemSum.toPlainString());
        }
        FinAccount account = accountMapper.selectById(form.accountId());
        if (account == null || account.getStatus() == null || account.getStatus() != 1) {
            throw BusinessException.ofKey("erp.fin.pay-account-not-exists");
        }
        BookContext.checkBookOwnership(account.getBookId(),
                "付款账户[%s %s]".formatted(account.getAccountNo(), account.getName()));

        // 1.5 预算占用校验（finance→budget 接线，两段式之【先校验】）：
        // 归属定位不到（部门/科目缺失）整体跳过；BLOCK 超预算在核销前抛异常整体拒绝；
        // WARN 超预算放行但 remark 追加提示
        BudgetDim budgetDim = resolveBudgetDim(form, bookId);
        String remark = form.remark();
        if (budgetDim != null) {
            BudgetCheckResult check = budgetControlService.check(
                    budgetDim.deptId(), budgetDim.subjectId(),
                    form.bizDate().getYear(), form.bizDate().getMonthValue(), amount);
            if ("WARN".equals(check.getLevel())) {
                String warnTip = "⚠超预算：占用率" + check.getRatioPercent() + "%";
                remark = appendRemark(remark, warnTip);
                // warn：超预算放行属控制配置决策，必须留痕（占用明细见 check.message）
                log.warn("[付款核销][预算警告] 付款单(供应商id={}) {}（阈值{}%），已放行并备注",
                        form.supplierId(), check.getMessage(),
                        check.getThresholdPercent() == null ? "-" : check.getThresholdPercent());
            }
        } else {
            log.info("[付款核销][预算校验跳过] 供应商id={}：未定位到 部门×科目 归属（老数据兼容）",
                    form.supplierId());
        }

        // 2. 逐笔核销应付（归属校验 + 条件 UPDATE）
        for (PaymentForm.Item item : form.items()) {
            FinPayable payable = payableMapper.selectById(item.payableId());
            if (payable == null) {
                throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.payable-not-exists", item.payableId());
            }
            BookContext.checkBookOwnership(payable.getBookId(), "应付单[%s]".formatted(payable.getBizNo()));
            if (!payable.getSupplierId().equals(form.supplierId())) {
                throw BusinessException.ofKey("erp.fin.payable-not-same-supplier", payable.getBizNo());
            }
            int rows = payableMapper.applyVerify(bookId, item.payableId(), item.verifyAmount());
            if (rows == 0) {
                // warn：核销超额说明应付余额被并发占用或前端数据过期，留痕便于对因
                log.warn("[付款核销][拒绝] 应付单={} 原因=核销额{}超过未核销余额{}",
                        payable.getBizNo(), item.verifyAmount().toPlainString(),
                        payable.getAmount().subtract(payable.getSettledAmount()).toPlainString());
                throw BusinessException.ofKey("erp.fin.payable-writeoff-over", payable.getBizNo(), item.verifyAmount().toPlainString(), payable.getAmount().subtract(payable.getSettledAmount()).toPlainString());
            }
        }

        // 3. 账户余额原子扣减（余额不足影响 0 行 → 回滚）
        int rows = accountMapper.changeBalance(bookId, form.accountId(), amount.negate(), amount);
        if (rows == 0) {
            // warn：透支拒绝是资金安全红线，谁在何时试图超余额付款必须可审计
            log.warn("[付款核销][拒绝] 账户={}({}) 原因=余额不足 本次付款={}",
                    account.getAccountNo(), account.getName(), amount.toPlainString());
            throw BusinessException.ofKey("erp.fin.account-balance-not-enough", account.getName());
        }

        // 4. 付款单主子表落库
        FinPayment payment = new FinPayment();
        payment.setBookId(bookId);
        payment.setPaymentNo(bizNoGenerator.next(FinanceBizNoGenerator.PAYMENT));
        payment.setSupplierId(form.supplierId());
        payment.setAccountId(form.accountId());
        payment.setBizDate(form.bizDate());
        payment.setAmount(amount);
        payment.setVerifiedAmount(amount);
        payment.setRemark(remark);
        if (budgetDim != null) {
            payment.setDeptId(budgetDim.deptId());
            payment.setSubjectId(budgetDim.subjectId());
        }
        paymentMapper.insert(payment);
        for (PaymentForm.Item item : form.items()) {
            FinPaymentItem paymentItem = new FinPaymentItem();
            paymentItem.setBookId(bookId);
            paymentItem.setPaymentId(payment.getId());
            paymentItem.setPayableId(item.payableId());
            paymentItem.setVerifyAmount(item.verifyAmount());
            paymentItemMapper.insert(paymentItem);
        }

        // 5. 账户流水
        FinAccount after = accountMapper.selectById(form.accountId());
        FinAccountFlow flow = new FinAccountFlow();
        flow.setBookId(bookId);
        flow.setAccountId(form.accountId());
        flow.setBizDate(form.bizDate());
        flow.setBizType("PAYMENT");
        flow.setBizId(payment.getId());
        flow.setBizNo(payment.getPaymentNo());
        flow.setDirection("EXPENSE");
        flow.setAmount(amount);
        flow.setBalanceAfter(after.getCurrentBalance());
        flow.setRemark("付款核销：" + payment.getPaymentNo());
        flowMapper.insert(flow);

        // 6. 预算执行数登记（两段式之【后推数】）：同一事务，付款回滚则登记一并回滚
        if (budgetDim != null) {
            BudgetUsageRecordForm usage = new BudgetUsageRecordForm();
            usage.setDeptId(budgetDim.deptId());
            usage.setSubjectId(budgetDim.subjectId());
            usage.setYear(form.bizDate().getYear());
            usage.setMonth(form.bizDate().getMonthValue());
            usage.setAmount(amount);
            usage.setSourceType("PAYMENT");
            usage.setSourceId(payment.getId());
            usage.setSourceNo(payment.getPaymentNo());
            usage.setBizDate(form.bizDate());
            usage.setRemark("付款核销自动登记");
            budgetControlService.record(usage);
        }
        // 审计日志：完成行带付款单号与付款后余额，与 fin_account_flow 流水互为印证
        log.info("[付款核销][完成] 付款单={} 金额={} 核销笔数={} 账户余额={} 预算登记={}",
                payment.getPaymentNo(), amount.toPlainString(), form.items().size(),
                after.getCurrentBalance().toPlainString(), budgetDim != null);
        return payment.getId();
    }

    /** 付款单必须存在且属于当前账套 */
    private FinPayment requirePayment(Long id) {
        FinPayment payment = paymentMapper.selectById(id);
        if (payment == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.payment-not-exists", id);
        }
        BookContext.checkBookOwnership(payment.getBookId(), "付款单[%s]".formatted(payment.getPaymentNo()));
        return payment;
    }

    /** 账户 ID 集合 → id -> 名称 映射 */
    private Map<Long, String> accountNames(List<Long> accountIds) {
        return accountIds.isEmpty() ? Map.of()
                : accountMapper.selectBatchIds(accountIds).stream()
                        .collect(Collectors.toMap(FinAccount::getId, FinAccount::getName));
    }

    // ==================== 预算归属推导与工具 ====================

    /** 付款的预算归属四元组（deptId+subjectId 均定位到才参与预算校验/登记） */
    private record BudgetDim(Long deptId, Long subjectId) {
    }

    /**
     * 解析付款的预算归属：表单显式 deptId 优先；为空时按「供应商→最近采购单（兜底
     * 最近应付单）创建人」推导部门。科目只认表单显式值（付款本身不携带科目语义）。
     * 部门或科目任一缺失 → 返回 null（跳过预算校验，兼容老数据）。
     */
    private BudgetDim resolveBudgetDim(PaymentForm form, Long bookId) {
        Long subjectId = form.subjectId();
        if (subjectId == null) {
            return null;
        }
        Long deptId = form.deptId();
        if (deptId == null) {
            deptId = deriveDeptFromSupplier(form.supplierId(), bookId);
        }
        return deptId == null ? null : new BudgetDim(deptId, subjectId);
    }

    /** 供应商 → 最近采购单（优先）/最近应付单（兜底）创建人 → 所属部门；定位不到返回 null */
    private Long deriveDeptFromSupplier(Long supplierId, Long bookId) {
        Long creator = null;
        if (purOrderTableAvailable) {
            try {
                creator = budgetIntegrationMapper.selectLatestPurOrderCreator(supplierId);
            } catch (BadSqlGrammarException e) {
                // 隔离环境 pur_order 未落库（purchase 模块 V201 缺失），降级跳过该口径
                purOrderTableAvailable = false;
                log.warn("pur_order 表不存在，付款预算归属跳过『最近采购单』口径");
            }
        }
        if (creator == null) {
            creator = budgetIntegrationMapper.selectLatestPayableCreator(bookId, supplierId);
        }
        if (creator == null || creator == 0) {
            return null;
        }
        return budgetIntegrationMapper.selectUserDeptId(creator);
    }

    /** remark 追加提示并按列宽 200 截断（NULL 安全） */
    private static String appendRemark(String remark, String tip) {
        String base = remark == null || remark.isBlank() ? "" : remark.trim();
        String merged = base.isBlank() ? tip : base + "｜" + tip;
        return merged.length() <= REMARK_MAX_LEN ? merged : merged.substring(0, REMARK_MAX_LEN);
    }
}
