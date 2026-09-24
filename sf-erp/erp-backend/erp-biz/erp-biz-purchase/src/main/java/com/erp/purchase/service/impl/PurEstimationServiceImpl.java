package com.erp.purchase.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.purchase.dto.form.EstimationReverseForm;
import com.erp.purchase.dto.query.PurEstimationQuery;
import com.erp.purchase.dto.vo.PurEstimationSummaryVO;
import com.erp.purchase.dto.vo.PurEstimationVO;
import com.erp.purchase.entity.FinPayable;
import com.erp.purchase.entity.PurEstimation;
import com.erp.purchase.entity.PurInstock;
import com.erp.purchase.entity.PurInstockDetail;
import com.erp.purchase.event.PurInstockAuditedEvent;
import com.erp.purchase.mapper.PurEstimationMapper;
import com.erp.purchase.mapper.PurFinPayableMapper;
import com.erp.purchase.mapper.PurInstockDetailMapper;
import com.erp.purchase.mapper.PurInstockMapper;
import com.erp.purchase.mapper.PurInvoiceRefMapper;
import com.erp.purchase.service.OrderNoGenerator;
import com.erp.purchase.service.PurEstimationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 采购暂估应付（货到票未到）Service 实现——V231，对标金蝶暂估入库。
 *
 * <h3>口径</h3>
 * <ul>
 *   <li><b>暂估金额</b>（不含税净额）= Σ 入库明细 qty × 含税单价 ÷ (1 + 税率%)，
 *       逐行 HALF_UP 2 位后合计再收口 2 位；</li>
 *   <li><b>暂估生成</b>：入库单已审核 + 无生效（待认证/已认证）蓝字进项发票。
 *       instant 模式在入库审核事务提交后（AFTER_COMMIT 事件）即时登记；
 *       month-end 模式由扫描器在月初补提已关账月份；扫描器同时兜底补提 instant 漏单。
 *       生成即写一条 fin_payable（biz_no=ZG 单号，amount=暂估金额，remark 标暂估）；</li>
 *   <li><b>票到冲回</b>（发票实际金额=价税合计，应付口径）：
 *       本次冲回暂估 cover = min(发票实际金额, 未冲回余额)，写 fin_payable 三类行——
 *       红字行（-cover，冲暂估）、正式行（+发票实际金额，票到入账）、
 *       差额调整行（发票实际 ≠ cover 时记差额，含税差与价差）；冲回进度用条件 UPDATE
 *       防并发超额/重复；暂估状态 0暂估中 → 10已冲回 / 20部分冲回；</li>
 *   <li><b>fin_payable 边界</b>：finance 模块约定表，本模块只 INSERT 不 UPDATE/DELETE；
 *       biz_no 前缀 {@code ZG} 的行即暂估链路（暂估/红冲/差额），供并行任务凭证引擎
 *       按 biz_no 前缀识别来源，两边不冲突。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurEstimationServiceImpl implements PurEstimationService {

    /** 单号前缀：暂估应付单 */
    public static final String PREFIX_ESTIMATION = "ZG";
    /** 暂估模式：审核即暂估（默认） */
    public static final String MODE_INSTANT = "instant";
    /** 暂估模式：月末/月初汇总暂估（扫描器只补提已关账月份） */
    public static final String MODE_MONTH_END = "month-end";

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT);

    private final PurEstimationMapper estimationMapper;
    private final PurInstockMapper instockMapper;
    private final PurInstockDetailMapper instockDetailMapper;
    private final PurInvoiceRefMapper invoiceRefMapper;
    private final PurFinPayableMapper finPayableMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final TransactionTemplate transactionTemplate;

    /** 暂估开关：purchase.estimation.enabled，默认开启 */
    @Value("${purchase.estimation.enabled:true}")
    private boolean enabled;

    /** 暂估模式：purchase.estimation.mode，instant(默认)/month-end */
    @Value("${purchase.estimation.mode:instant}")
    private String mode;

    // ==================== 事件挂点（入库审核 AFTER_COMMIT） ====================

    /**
     * 入库审核事务提交后即时暂估（instant 模式）。
     * <p>
     * AFTER_COMMIT + 自管事务（TransactionTemplate）：暂估登记失败【不回滚】已成功的
     * 入库审核（库存/应付/订单回写保持原子），失败单据由扫描器兜底补提。
     * 监听器内吞异常，避免向已提交的审核调用方冒泡。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInstockAudited(PurInstockAuditedEvent event) {
        if (!enabled || !MODE_INSTANT.equals(mode)) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(s -> createIfEligible(event.instockId(), "入库审核即时暂估"));
        } catch (Exception ex) {
            log.warn("[暂估][事件] 入库单id={} 即时暂估失败（扫描器将兜底补提）：{}", event.instockId(), ex.getMessage());
        }
    }

    // ==================== 查询/报表 ====================

    @Override
    public PageResult<PurEstimationVO> page(PurEstimationQuery query) {
        var page = estimationMapper.selectEstimationPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PurEstimationSummaryVO summary(String month) {
        String period = normalizePeriod(month);
        PurEstimationSummaryVO vo = new PurEstimationSummaryVO();
        vo.setPeriod(period);
        List<PurEstimationSummaryVO.SupplierRow> rows = estimationMapper.summaryBySupplier(period);
        vo.setSuppliers(rows);
        vo.setTotalAmount(sum(rows, PurEstimationSummaryVO.SupplierRow::getTotalAmount));
        vo.setReversedAmount(sum(rows, PurEstimationSummaryVO.SupplierRow::getReversedAmount));
        // 暂估余额 = 未冲回余额（已冲回部分不再挂账）
        vo.setOpenAmount(sum(rows, PurEstimationSummaryVO.SupplierRow::getOpenAmount));
        return vo;
    }

    // ==================== 暂估生成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromInstock(Long instockId) {
        PurInstock instock = requireAuditedInstock(instockId);
        if (countByInstock(instockId) > 0) {
            throw BusinessException.ofKey("erp.pur.accrual-exists", instock.getInstockNo());
        }
        if (invoiceRefMapper.countActiveBlueInvoices(instockId) > 0) {
            throw BusinessException.ofKey("erp.pur.accrual-not-needed", instock.getInstockNo());
        }
        return insertEstimation(instock, "手工生成");
    }

    /** 扫描器/事件入口：符合条件（已审核+无暂估+无生效发票）才登记，不符合静默跳过 */
    private Long createIfEligible(Long instockId, String source) {
        PurInstock instock = instockMapper.selectById(instockId);
        if (instock == null || instock.getStatus() != 10) {
            return null;
        }
        if (countByInstock(instockId) > 0 || invoiceRefMapper.countActiveBlueInvoices(instockId) > 0) {
            return null;
        }
        return insertEstimation(instock, source);
    }

    /** 落库暂估单 + fin_payable 暂估行（调用方须已持有事务） */
    private Long insertEstimation(PurInstock instock, String source) {
        List<PurInstockDetail> details = instockDetailMapper.selectList(
                Wrappers.lambdaQuery(PurInstockDetail.class).eq(PurInstockDetail::getInstockId, instock.getId()));
        if (details == null || details.isEmpty()) {
            throw BusinessException.ofKey("erp.pur.accrual-no-lines", instock.getInstockNo());
        }
        BigDecimal amount = details.stream()
                .map(PurEstimationServiceImpl::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        PurEstimation e = new PurEstimation();
        e.setEstimationNo(orderNoGenerator.next(PREFIX_ESTIMATION, 3));
        e.setInstockId(instock.getId());
        e.setInstockNo(instock.getInstockNo());
        e.setSupplierId(instock.getSupplierId());
        e.setAmount(amount);
        e.setReversedAmount(BigDecimal.ZERO);
        e.setStatus(PurEstimation.STATUS_OPEN);
        // 暂估期间 = 入库单入库月份（月初补提上月单据时即归属上月期间）
        e.setPeriod(instock.getInstockDate().format(PERIOD_FMT));
        e.setRemark("暂估应付（" + source + "）");
        try {
            estimationMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            // uk(instock_id) 兜底：事件与扫描器并发时后到者放弃
            throw BusinessException.ofKey("erp.pur.accrual-exists-concurrent", instock.getInstockNo());
        }
        // fin_payable 暂估行：biz_no=ZG 单号（前缀 ZG 即暂估链路标识）
        insertPayable(instock.getSupplierId(), e.getEstimationNo(), instock.getInstockDate(), amount,
                "暂估应付（入库单 " + instock.getInstockNo() + "，" + source + "，货到票未到）");
        log.info("[暂估][登记] 单号={} 入库单={} 供应商id={} 暂估金额={} 来源={}",
                e.getEstimationNo(), instock.getInstockNo(), instock.getSupplierId(),
                amount.toPlainString(), source);
        return e.getId();
    }

    /**
     * 明细不含税净额 = qty × 含税单价 ÷ (1 + 税率%)，HALF_UP 2 位（税率 0 直接取含税额）。
     */
    private static BigDecimal netAmount(PurInstockDetail d) {
        BigDecimal gross = d.getQty().multiply(d.getPrice());
        BigDecimal rate = d.getTaxRate() == null ? BigDecimal.ZERO : d.getTaxRate();
        if (rate.signum() <= 0) {
            return gross.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal divisor = BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return gross.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    // ==================== 票到冲回 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id, EstimationReverseForm form) {
        PurEstimation e = requireEstimation(id);
        reverseInternal(e, form.getInvoiceNo().trim(), form.getActualAmount());
    }

    /**
     * 冲回核心（手动/自动共用，调用方须已持有事务）：
     * 红字行 -cover + 正式行 +发票实际金额 + 差额行（实际≠cover 时）。
     */
    private void reverseInternal(PurEstimation e, String invoiceNo, BigDecimal actualTotal) {
        if (e.getStatus() == PurEstimation.STATUS_REVERSED) {
            throw BusinessException.ofKey("erp.pur.accrual-fully-reversed", e.getEstimationNo());
        }
        if (invoiceUsed(e.getReversedInvoiceNos(), invoiceNo)) {
            throw BusinessException.ofKey("erp.pur.invoice-accrual-reversed", invoiceNo, e.getEstimationNo());
        }
        BigDecimal remain = e.getAmount().subtract(e.getReversedAmount());
        if (remain.signum() <= 0) {
            throw BusinessException.ofKey("erp.pur.accrual-no-balance-no-reverse", e.getEstimationNo());
        }
        if (actualTotal == null || actualTotal.signum() <= 0) {
            throw BusinessException.ofKey("erp.pur.invoice-amount-positive");
        }
        // 本次冲回暂估金额：发票实际金额覆盖多少暂估冲多少，超出部分进差额行
        BigDecimal cover = actualTotal.min(remain);
        int newStatus = e.getReversedAmount().add(cover).compareTo(e.getAmount()) >= 0
                ? PurEstimation.STATUS_REVERSED : PurEstimation.STATUS_PARTIAL;
        int rows = estimationMapper.addReversedAmount(e.getId(), cover, newStatus, invoiceNo,
                appendInvoiceNo(e.getReversedInvoiceNos(), invoiceNo));
        if (rows == 0) {
            throw BusinessException.ofKey("erp.pur.accrual-status-changed", e.getEstimationNo());
        }
        LocalDate today = LocalDate.now();
        // 1) 红字行：冲暂估（负金额）
        insertPayable(e.getSupplierId(), e.getEstimationNo(), today, cover.negate(),
                "暂估红冲：发票 " + invoiceNo + " 冲回暂估 " + cover.toPlainString());
        // 2) 正式行：票到按发票实际金额入账（biz_no=入库单号，与入库应付同源可对账）
        insertPayable(e.getSupplierId(), e.getInstockNo(), today, actualTotal,
                "票到入账：发票 " + invoiceNo + " 实际金额 " + actualTotal.toPlainString());
        // 3) 差额调整行：发票实际（价税合计）与本次冲回暂估（不含税）的口径差（价差+税差）
        BigDecimal diff = actualTotal.subtract(cover);
        if (diff.signum() != 0) {
            insertPayable(e.getSupplierId(), e.getEstimationNo(), today, diff,
                    "暂估差额调整：暂估 " + e.getAmount().toPlainString()
                            + "，本次冲回 " + cover.toPlainString()
                            + "，发票实际 " + actualTotal.toPlainString());
        }
        log.info("[暂估][冲回] 单号={} 发票={} 实际金额={} 冲回暂估={} 差额={} 新状态={}",
                e.getEstimationNo(), invoiceNo, actualTotal.toPlainString(), cover.toPlainString(),
                diff.toPlainString(), newStatus);
    }

    /** fin_payable INSERT（约定表只写不改；status 用 finance 现有枚举 UNSETTLED） */
    private void insertPayable(Long supplierId, String bizNo, LocalDate bizDate,
                               BigDecimal amount, String remark) {
        FinPayable p = new FinPayable();
        p.setSupplierId(supplierId);
        p.setBizNo(bizNo);
        p.setBizDate(bizDate);
        p.setAmount(amount);
        p.setSettledAmount(BigDecimal.ZERO);
        p.setStatus(FinPayable.STATUS_UNSETTLED);
        p.setRemark(remark);
        finPayableMapper.insert(p);
    }

    // ==================== 扫描器 ====================

    @Override
    public void scanOnce() {
        if (!enabled) {
            log.debug("[暂估][扫描器] 已关闭（purchase.estimation.enabled=false），跳过");
            return;
        }
        scanMissingEstimations();
        scanInvoicesForReverse();
    }

    /** 补提漏单暂估：instant 补全部漏单；month-end 只补已关账月份（月初补提） */
    private void scanMissingEstimations() {
        boolean monthEnd = MODE_MONTH_END.equals(mode);
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        List<Long> ids = estimationMapper.selectInstockIdsWithoutEstimation(monthEnd, monthStart);
        for (Long instockId : ids) {
            try {
                transactionTemplate.executeWithoutResult(s -> createIfEligible(instockId,
                        monthEnd ? "月初汇总暂估(month-end)" : "扫描器补提暂估(instant)"));
            } catch (Exception ex) {
                log.warn("[暂估][扫描器] 入库单id={} 补提失败：{}", instockId, ex.getMessage());
            }
        }
        if (!ids.isEmpty()) {
            log.info("[暂估][扫描器] 补提候选 {} 单（模式={}）", ids.size(), mode);
        }
    }

    /** 已认证蓝字发票自动冲回：发票关联入库单存在未冲完暂估且该发票未冲回过 */
    private void scanInvoicesForReverse() {
        List<Long> invoiceIds = estimationMapper.selectAuthedInvoiceIdsForReverse();
        for (Long invoiceId : invoiceIds) {
            try {
                transactionTemplate.executeWithoutResult(s -> reverseByInvoice(invoiceId));
            } catch (Exception ex) {
                log.warn("[暂估][扫描器] 发票id={} 自动冲回失败：{}", invoiceId, ex.getMessage());
            }
        }
        if (!invoiceIds.isEmpty()) {
            log.info("[暂估][扫描器] 冲回候选 {} 张发票", invoiceIds.size());
        }
    }

    /** 按发票冲回（扫描器事务内）：actualTotal=发票价税合计 */
    private void reverseByInvoice(Long invoiceId) {
        String invoiceNo = invoiceRefMapper.selectInvoiceNoById(invoiceId);
        BigDecimal actualTotal = invoiceRefMapper.selectInvoiceTotalById(invoiceId);
        Long instockId = invoiceRefMapper.selectInstockIdByInvoice(invoiceId);
        if (invoiceNo == null || actualTotal == null || instockId == null) {
            return;
        }
        PurEstimation e = estimationMapper.selectOne(Wrappers.lambdaQuery(PurEstimation.class)
                .eq(PurEstimation::getInstockId, instockId)
                .in(PurEstimation::getStatus, PurEstimation.STATUS_OPEN, PurEstimation.STATUS_PARTIAL)
                .last("LIMIT 1"));
        if (e == null || invoiceUsed(e.getReversedInvoiceNos(), invoiceNo)) {
            return;
        }
        reverseInternal(e, invoiceNo, actualTotal);
    }

    // ==================== 私有工具 ====================

    private PurInstock requireAuditedInstock(Long instockId) {
        PurInstock instock = instockMapper.selectById(instockId);
        if (instock == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.invoice.pur-instock-not-exists", instockId);
        }
        if (instock.getStatus() != 10) {
            throw BusinessException.ofKey("erp.pur.instock-not-audited-no-accrual", instock.getInstockNo());
        }
        return instock;
    }

    private PurEstimation requireEstimation(Long id) {
        PurEstimation e = estimationMapper.selectById(id);
        if (e == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.pur.accrual-not-exists", id);
        }
        return e;
    }

    private long countByInstock(Long instockId) {
        return estimationMapper.selectCount(Wrappers.lambdaQuery(PurEstimation.class)
                .eq(PurEstimation::getInstockId, instockId));
    }

    /** 发票号是否已在冲回清单（逗号分隔比对） */
    private static boolean invoiceUsed(String reversedInvoiceNos, String invoiceNo) {
        if (reversedInvoiceNos == null || reversedInvoiceNos.isBlank()) {
            return false;
        }
        return Arrays.stream(reversedInvoiceNos.split(","))
                .map(String::trim)
                .anyMatch(invoiceNo::equals);
    }

    /** 追加冲回发票号（清单超长截断，防列溢出） */
    private static String appendInvoiceNo(String reversedInvoiceNos, String invoiceNo) {
        String base = reversedInvoiceNos == null || reversedInvoiceNos.isBlank() ? "" : reversedInvoiceNos;
        String next = base.isEmpty() ? invoiceNo : base + "," + invoiceNo;
        return next.length() > 500 ? next.substring(0, 500) : next;
    }

    /** 期间归一化：yyyyMM，缺省当前月 */
    private static String normalizePeriod(String month) {
        if (month == null || month.isBlank()) {
            return LocalDate.now().format(PERIOD_FMT);
        }
        String trimmed = month.trim();
        if (!trimmed.matches("\\d{6}")) {
            throw BusinessException.ofKey("erp.pur.month-format-yyyymm", trimmed);
        }
        return trimmed;
    }

    private static BigDecimal sum(List<PurEstimationSummaryVO.SupplierRow> rows,
                                  java.util.function.Function<PurEstimationSummaryVO.SupplierRow, BigDecimal> getter) {
        return rows.stream()
                .map(r -> getter.apply(r) == null ? BigDecimal.ZERO : getter.apply(r))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
