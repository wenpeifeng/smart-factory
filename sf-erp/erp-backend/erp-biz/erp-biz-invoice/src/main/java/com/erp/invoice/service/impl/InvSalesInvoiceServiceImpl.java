package com.erp.invoice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.invoice.constant.InvoiceConstants;
import com.erp.invoice.dto.form.SalesInvoiceApplyForm;
import com.erp.invoice.dto.request.SalesInvoiceQuery;
import com.erp.invoice.dto.vo.OutstockRawVO;
import com.erp.invoice.dto.vo.SalesInvoiceVO;
import com.erp.invoice.dto.vo.ToBeInvoicedVO;
import com.erp.invoice.entity.InvSalesInvoice;
import com.erp.invoice.mapper.InvQueryMapper;
import com.erp.invoice.mapper.InvSalesInvoiceMapper;
import com.erp.invoice.service.InvSalesInvoiceService;
import com.erp.invoice.support.CurrentUser;
import com.erp.invoice.support.InvoiceNoGenerator;
import com.erp.invoice.support.InvoicePartnerNameLookup;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 销项发票服务实现。
 * <p>
 * 跨模块只读 sal_outstock / sal_outstock_detail（InvQueryMapper，并行协作约定）；
 * 状态流转用条件 UPDATE 防并发；红冲在同一事务内完成"原票置已红冲 + 插入负数红字票"。
 */
@Service
@RequiredArgsConstructor
public class InvSalesInvoiceServiceImpl implements InvSalesInvoiceService {

    private final InvSalesInvoiceMapper salesInvoiceMapper;
    private final InvQueryMapper invQueryMapper;
    private final InvoiceNoGenerator invoiceNoGenerator;
    private final InvoicePartnerNameLookup partnerNameLookup;

    @Override
    public PageResult<SalesInvoiceVO> page(SalesInvoiceQuery query, long pageNum, long pageSize) {
        var wrapper = Wrappers.lambdaQuery(InvSalesInvoice.class)
                .eq(query.customerId() != null, InvSalesInvoice::getCustomerId, query.customerId())
                .eq(query.status() != null, InvSalesInvoice::getStatus, query.status())
                // 关键词同时匹配发票号码与业务单号
                .and(notBlank(query.keyword()), w -> w
                        .like(InvSalesInvoice::getInvoiceNo, trim(query.keyword()))
                        .or()
                        .like(InvSalesInvoice::getBizNo, trim(query.keyword())))
                .ge(query.from() != null, InvSalesInvoice::getInvoiceDate, query.from())
                .le(query.to() != null, InvSalesInvoice::getInvoiceDate, query.to())
                .orderByDesc(InvSalesInvoice::getId);

        PageResult<InvSalesInvoice> page = PageUtils.of(salesInvoiceMapper, wrapper, pageNum, pageSize);
        Map<Long, String> names = partnerNameLookup.customerNames(
                page.getRecords().stream().map(InvSalesInvoice::getCustomerId).toList());
        return page.map(e -> SalesInvoiceVO.from(e, names.get(e.getCustomerId())));
    }

    @Override
    public SalesInvoiceVO detail(Long id) {
        InvSalesInvoice invoice = requireInvoice(id);
        Map<Long, String> names = partnerNameLookup.customerNames(List.of(invoice.getCustomerId()));
        return SalesInvoiceVO.from(invoice, names.get(invoice.getCustomerId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long applyFromOrder(Long outstockId, SalesInvoiceApplyForm form) {
        OutstockRawVO outstock = invQueryMapper.selectOutstock(outstockId);
        if (outstock == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.invoice.sal-outstock-not-exists", outstockId);
        }
        if (outstock.status() != InvoiceConstants.OUTSTOCK_AUDITED) {
            throw BusinessException.ofKey("erp.invoice.outstock-not-audited-no-issue", outstock.outNo());
        }
        if (countActiveBlueInvoices(outstockId) > 0) {
            throw BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.invoice.outstock-invoice-exists", outstock.outNo());
        }

        // 不含税金额以出库明细汇总为准，明细缺失回退单头合计
        BigDecimal detailAmount = invQueryMapper.sumOutstockDetailAmount(outstockId);
        BigDecimal amount = detailAmount.compareTo(BigDecimal.ZERO) > 0
                ? detailAmount : outstock.totalAmount();

        BigDecimal taxRate = resolveTaxRate(form == null ? null : form.taxRate());

        InvSalesInvoice invoice = new InvSalesInvoice();
        invoice.setInvoiceNo(invoiceNoGenerator.next());
        invoice.setCustomerId(outstock.customerId());
        invoice.setBizType(InvoiceConstants.BIZ_SAL_OUTSTOCK);
        invoice.setBizId(outstock.id());
        invoice.setBizNo(outstock.outNo());
        invoice.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        invoice.setTaxRate(taxRate);
        invoice.setTaxAmount(calcTax(amount, taxRate));
        invoice.setTotalAmount(amount.setScale(2, RoundingMode.HALF_UP).add(invoice.getTaxAmount()));
        invoice.setRedFlag(InvoiceConstants.BLUE);
        invoice.setStatus(InvoiceConstants.STATUS_PENDING);
        invoice.setRemark(form == null ? null : form.remark());
        salesInvoiceMapper.insert(invoice);
        return invoice.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void issue(Long id) {
        int rows = salesInvoiceMapper.issueInvoice(id, LocalDate.now(), CurrentUser.userId(), LocalDateTime.now());
        if (rows == 0) {
            InvSalesInvoice invoice = salesInvoiceMapper.selectById(id);
            if (invoice == null) {
                throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.invoice.output-invoice-not-exists", id);
            }
            throw BusinessException.ofKey("erp.invoice.issue-status-not-allowed", invoice.getInvoiceNo());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id) {
        InvSalesInvoice origin = requireInvoice(id);
        if (origin.getRedFlag() == InvoiceConstants.RED) {
            throw BusinessException.ofKey("erp.invoice.red-invoice-no-re-reverse", origin.getInvoiceNo());
        }
        if (origin.getStatus() != InvoiceConstants.STATUS_ISSUED_OR_AUTHED) {
            throw BusinessException.ofKey("erp.invoice.reverse-issued-only", origin.getInvoiceNo());
        }

        // 1. 原票条件流转 10→20，防并发重复红冲
        int rows = salesInvoiceMapper.reverseInvoice(id);
        if (rows == 0) {
            throw BusinessException.ofKey("erp.invoice.status-changed-reverse-failed");
        }

        // 2. 插入负数红字发票（金额/税额/价税合计一律取负，开票日期=红冲当日）
        InvSalesInvoice red = new InvSalesInvoice();
        red.setInvoiceNo(invoiceNoGenerator.next());
        red.setInvoiceDate(LocalDate.now());
        red.setCustomerId(origin.getCustomerId());
        red.setBizType(origin.getBizType());
        red.setBizId(origin.getBizId());
        red.setBizNo(origin.getBizNo());
        red.setAmount(origin.getAmount().negate());
        red.setTaxRate(origin.getTaxRate());
        red.setTaxAmount(origin.getTaxAmount().negate());
        red.setTotalAmount(origin.getTotalAmount().negate());
        red.setRedFlag(InvoiceConstants.RED);
        red.setSourceInvoiceId(origin.getId());
        red.setSourceInvoiceNo(origin.getInvoiceNo());
        red.setStatus(InvoiceConstants.STATUS_ISSUED_OR_AUTHED);
        red.setIssueBy(CurrentUser.userId());
        red.setIssueTime(LocalDateTime.now());
        red.setRemark("红冲发票 %s".formatted(origin.getInvoiceNo()));
        salesInvoiceMapper.insert(red);
    }

    @Override
    public List<ToBeInvoicedVO> toBeInvoiced(Long customerId) {
        List<ToBeInvoicedVO> rows = invQueryMapper.selectToBeInvoicedOutstocks(customerId);
        Map<Long, String> names = partnerNameLookup.customerNames(
                rows.stream().map(ToBeInvoicedVO::customerId).toList());
        return rows.stream()
                .map(r -> new ToBeInvoicedVO(r.outstockId(), r.outNo(), r.customerId(),
                        names.get(r.customerId()), r.outDate(), r.amount()))
                .toList();
    }

    // ==================== 私有方法 ====================

    /** 加载发票，不存在抛业务异常 */
    private InvSalesInvoice requireInvoice(Long id) {
        InvSalesInvoice invoice = salesInvoiceMapper.selectById(id);
        if (invoice == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.invoice.output-invoice-not-exists", id);
        }
        return invoice;
    }

    /** 统计出库单名下未红冲蓝字发票数（待开票/已开票均占用，已红冲不占用） */
    private Long countActiveBlueInvoices(Long outstockId) {
        return salesInvoiceMapper.selectCount(Wrappers.lambdaQuery(InvSalesInvoice.class)
                .eq(InvSalesInvoice::getBizType, InvoiceConstants.BIZ_SAL_OUTSTOCK)
                .eq(InvSalesInvoice::getBizId, outstockId)
                .eq(InvSalesInvoice::getRedFlag, InvoiceConstants.BLUE)
                .in(InvSalesInvoice::getStatus,
                        InvoiceConstants.STATUS_PENDING, InvoiceConstants.STATUS_ISSUED_OR_AUTHED));
    }

    /** 解析税率：空取默认 13%，非 13/9/6 拒绝（比较用 stripTrailingZeros 归一） */
    private BigDecimal resolveTaxRate(BigDecimal taxRate) {
        if (taxRate == null) {
            return InvoiceConstants.DEFAULT_TAX_RATE;
        }
        boolean allowed = InvoiceConstants.ALLOWED_TAX_RATES.stream()
                .anyMatch(r -> r.compareTo(taxRate) == 0);
        if (!allowed) {
            throw BusinessException.ofKey("erp.invoice.tax-rate-invalid", taxRate.toPlainString());
        }
        return taxRate.setScale(2, RoundingMode.HALF_UP);
    }

    /** 税额 = 金额 × 税率% 四舍五入 HALF_UP 2 位 */
    private BigDecimal calcTax(BigDecimal amount, BigDecimal taxRate) {
        return amount.multiply(taxRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
