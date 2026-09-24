package com.erp.invoice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.invoice.constant.InvoiceConstants;
import com.erp.invoice.dto.form.PurchaseInvoiceRegisterForm;
import com.erp.invoice.dto.request.PurchaseInvoiceQuery;
import com.erp.invoice.dto.vo.InstockRawVO;
import com.erp.invoice.dto.vo.PurchaseInvoiceVO;
import com.erp.invoice.dto.vo.ToBeRegisteredVO;
import com.erp.invoice.entity.InvPurchaseInvoice;
import com.erp.invoice.mapper.InvPurchaseInvoiceMapper;
import com.erp.invoice.mapper.InvQueryMapper;
import com.erp.invoice.service.InvPurchaseInvoiceService;
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
import java.util.List;
import java.util.Map;

/**
 * 进项发票服务实现。
 * <p>
 * 跨模块只读 pur_instock / pur_instock_detail（InvQueryMapper，并行协作约定）；
 * 认证（0→10）与红冲（10→20）用条件 UPDATE 防并发；红冲在同一事务内插入负数红字票
 * （红字票直接置为已认证、认证日期取红冲当日，使已认证进项税额自然冲减）。
 */
@Service
@RequiredArgsConstructor
public class InvPurchaseInvoiceServiceImpl implements InvPurchaseInvoiceService {

    private final InvPurchaseInvoiceMapper purchaseInvoiceMapper;
    private final InvQueryMapper invQueryMapper;
    private final InvoiceNoGenerator invoiceNoGenerator;
    private final InvoicePartnerNameLookup partnerNameLookup;

    @Override
    public PageResult<PurchaseInvoiceVO> page(PurchaseInvoiceQuery query, long pageNum, long pageSize) {
        var wrapper = Wrappers.lambdaQuery(InvPurchaseInvoice.class)
                .eq(query.supplierId() != null, InvPurchaseInvoice::getSupplierId, query.supplierId())
                .eq(query.status() != null, InvPurchaseInvoice::getStatus, query.status())
                // 关键词同时匹配发票号码与业务单号
                .and(notBlank(query.keyword()), w -> w
                        .like(InvPurchaseInvoice::getInvoiceNo, trim(query.keyword()))
                        .or()
                        .like(InvPurchaseInvoice::getBizNo, trim(query.keyword())))
                .ge(query.from() != null, InvPurchaseInvoice::getInvoiceDate, query.from())
                .le(query.to() != null, InvPurchaseInvoice::getInvoiceDate, query.to())
                .orderByDesc(InvPurchaseInvoice::getId);

        PageResult<InvPurchaseInvoice> page = PageUtils.of(purchaseInvoiceMapper, wrapper, pageNum, pageSize);
        Map<Long, String> names = partnerNameLookup.supplierNames(
                page.getRecords().stream().map(InvPurchaseInvoice::getSupplierId).toList());
        return page.map(e -> PurchaseInvoiceVO.from(e, names.get(e.getSupplierId())));
    }

    @Override
    public PurchaseInvoiceVO detail(Long id) {
        InvPurchaseInvoice invoice = requireInvoice(id);
        Map<Long, String> names = partnerNameLookup.supplierNames(List.of(invoice.getSupplierId()));
        return PurchaseInvoiceVO.from(invoice, names.get(invoice.getSupplierId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long registerFromInstock(Long instockId, PurchaseInvoiceRegisterForm form) {
        InstockRawVO instock = invQueryMapper.selectInstock(instockId);
        if (instock == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.invoice.pur-instock-not-exists", instockId);
        }
        if (instock.status() != InvoiceConstants.INSTOCK_AUDITED) {
            throw BusinessException.ofKey("erp.invoice.instock-not-audited-no-register", instock.instockNo());
        }
        if (countActiveBlueInvoices(instockId) > 0) {
            throw BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.invoice.instock-invoice-exists", instock.instockNo());
        }

        BigDecimal amount = instock.totalAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxRate = resolveTaxRate(form == null ? null : form.taxRate(), instockId);

        InvPurchaseInvoice invoice = new InvPurchaseInvoice();
        // 票面号优先（供应商开出的真票号），缺省系统取号
        invoice.setInvoiceNo(form != null && notBlank(form.invoiceNo())
                ? trim(form.invoiceNo()) : invoiceNoGenerator.next());
        invoice.setInvoiceDate(form != null && form.invoiceDate() != null
                ? form.invoiceDate() : instock.instockDate());
        invoice.setSupplierId(instock.supplierId());
        invoice.setBizType(InvoiceConstants.BIZ_PUR_INSTOCK);
        invoice.setBizId(instock.id());
        invoice.setBizNo(instock.instockNo());
        invoice.setAmount(amount);
        invoice.setTaxRate(taxRate);
        invoice.setTaxAmount(amount.multiply(taxRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        invoice.setTotalAmount(amount.add(invoice.getTaxAmount()));
        invoice.setRedFlag(InvoiceConstants.BLUE);
        invoice.setStatus(InvoiceConstants.STATUS_PENDING);
        invoice.setRemark(form == null ? null : form.remark());
        purchaseInvoiceMapper.insert(invoice);
        return invoice.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void authenticate(Long id) {
        int rows = purchaseInvoiceMapper.authenticateInvoice(id, LocalDate.now(), CurrentUser.userId());
        if (rows == 0) {
            InvPurchaseInvoice invoice = purchaseInvoiceMapper.selectById(id);
            if (invoice == null) {
                throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.invoice.input-invoice-not-exists", id);
            }
            throw BusinessException.ofKey("erp.invoice.certify-status-not-allowed", invoice.getInvoiceNo());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id) {
        InvPurchaseInvoice origin = requireInvoice(id);
        if (origin.getRedFlag() == InvoiceConstants.RED) {
            throw BusinessException.ofKey("erp.invoice.red-invoice-no-re-reverse", origin.getInvoiceNo());
        }
        if (origin.getStatus() != InvoiceConstants.STATUS_ISSUED_OR_AUTHED) {
            throw BusinessException.ofKey("erp.invoice.reverse-status-not-allowed", origin.getInvoiceNo());
        }

        // 1. 原票条件流转 10→20，防并发重复红冲
        int rows = purchaseInvoiceMapper.reverseInvoice(id);
        if (rows == 0) {
            throw BusinessException.ofKey("erp.invoice.status-changed-reverse-failed");
        }

        // 2. 插入负数红字发票：直接置为已认证（认证日期=红冲当日），税负统计中自然冲减原票
        InvPurchaseInvoice red = new InvPurchaseInvoice();
        red.setInvoiceNo(invoiceNoGenerator.next());
        red.setInvoiceDate(LocalDate.now());
        red.setSupplierId(origin.getSupplierId());
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
        red.setAuthDate(LocalDate.now());
        red.setAuthBy(CurrentUser.userId());
        red.setRemark("红冲发票 %s".formatted(origin.getInvoiceNo()));
        purchaseInvoiceMapper.insert(red);
    }

    @Override
    public List<ToBeRegisteredVO> toBeRegistered(Long supplierId) {
        List<ToBeRegisteredVO> rows = invQueryMapper.selectToBeRegisteredInstocks(supplierId);
        Map<Long, String> names = partnerNameLookup.supplierNames(
                rows.stream().map(ToBeRegisteredVO::supplierId).toList());
        return rows.stream()
                .map(r -> new ToBeRegisteredVO(r.instockId(), r.instockNo(), r.supplierId(),
                        names.get(r.supplierId()), r.instockDate(), r.amount()))
                .toList();
    }

    // ==================== 私有方法 ====================

    /** 加载发票，不存在抛业务异常 */
    private InvPurchaseInvoice requireInvoice(Long id) {
        InvPurchaseInvoice invoice = purchaseInvoiceMapper.selectById(id);
        if (invoice == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.invoice.input-invoice-not-exists", id);
        }
        return invoice;
    }

    /** 统计入库单名下未红冲蓝字发票数（待认证/已认证均占用） */
    private Long countActiveBlueInvoices(Long instockId) {
        return purchaseInvoiceMapper.selectCount(Wrappers.lambdaQuery(InvPurchaseInvoice.class)
                .eq(InvPurchaseInvoice::getBizType, InvoiceConstants.BIZ_PUR_INSTOCK)
                .eq(InvPurchaseInvoice::getBizId, instockId)
                .eq(InvPurchaseInvoice::getRedFlag, InvoiceConstants.BLUE)
                .in(InvPurchaseInvoice::getStatus,
                        InvoiceConstants.STATUS_PENDING, InvoiceConstants.STATUS_ISSUED_OR_AUTHED));
    }

    /**
     * 解析税率：表单显式传入优先（须为 13/9/6）；
     * 缺省取入库明细唯一非 0 税率（明细税率混用时不猜测），否则 13%。
     */
    private BigDecimal resolveTaxRate(BigDecimal formRate, Long instockId) {
        if (formRate != null) {
            boolean allowed = InvoiceConstants.ALLOWED_TAX_RATES.stream()
                    .anyMatch(r -> r.compareTo(formRate) == 0);
            if (!allowed) {
                throw BusinessException.ofKey("erp.invoice.tax-rate-invalid", formRate.toPlainString());
            }
            return formRate.setScale(2, RoundingMode.HALF_UP);
        }
        return defaultRate(invQueryMapper.selectInstockDetailTaxRates(instockId));
    }

    /**
     * 入库明细税率 → 登记默认税率：仅当明细税率唯一且非 0 时采用，
     * 混合税率/全 0 时不猜测，回落 13%。
     */
    private BigDecimal defaultRate(List<BigDecimal> detailRates) {
        if (detailRates != null && detailRates.size() == 1) {
            BigDecimal rate = detailRates.get(0);
            if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
                return rate.setScale(2, RoundingMode.HALF_UP);
            }
        }
        return InvoiceConstants.DEFAULT_TAX_RATE;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
