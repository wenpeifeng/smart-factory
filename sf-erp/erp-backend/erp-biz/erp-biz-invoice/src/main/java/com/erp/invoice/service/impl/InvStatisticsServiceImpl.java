package com.erp.invoice.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.invoice.dto.vo.BizStatRawVO;
import com.erp.invoice.dto.vo.MonthlyRawVO;
import com.erp.invoice.dto.vo.MonthlyStatVO;
import com.erp.invoice.dto.vo.ReconciliationVO;
import com.erp.invoice.dto.vo.TaxStatRawVO;
import com.erp.invoice.dto.vo.TaxStatVO;
import com.erp.invoice.mapper.InvQueryMapper;
import com.erp.invoice.service.InvStatisticsService;
import com.erp.invoice.support.InvoicePartnerNameLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * 税务统计与对账辅助服务实现。
 * <p>
 * 统计口径（两张发票表一致）：status IN (10, 20) —— 待开票/待认证不计；
 * 已红冲蓝票仍计入，由红字负数票自然冲减，净额正确。期间归属：
 * 销项税额按 invoice_date、进项税额按 auth_date（认证所属期）；
 * 月度价税走势两向均按 invoice_date。
 */
@Service
@RequiredArgsConstructor
public class InvStatisticsServiceImpl implements InvStatisticsService {

    private final InvQueryMapper invQueryMapper;
    private final InvoicePartnerNameLookup partnerNameLookup;

    @Override
    public TaxStatVO tax(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate begin = from != null ? from : end.minusYears(1);

        TaxStatRawVO sales = invQueryMapper.selectSalesTaxStat(begin, end);
        TaxStatRawVO purchase = invQueryMapper.selectPurchaseTaxStat(begin, end);

        BigDecimal salesAmount = nvl(sales.amountSum());
        BigDecimal salesTax = nvl(sales.taxSum());
        BigDecimal purchaseTax = nvl(purchase.taxSum());
        BigDecimal vatPayable = salesTax.subtract(purchaseTax);

        // 税负率（%）= 应纳增值税 ÷ 不含税销售额 × 100；销售额为 0 时返回 0（避免除零）
        BigDecimal taxBurdenRate = salesAmount.signum() == 0
                ? BigDecimal.ZERO
                : vatPayable.multiply(BigDecimal.valueOf(100))
                        .divide(salesAmount, 2, RoundingMode.HALF_UP);

        return new TaxStatVO(begin, end, salesAmount, salesTax, purchaseTax, vatPayable, taxBurdenRate);
    }

    @Override
    public MonthlyStatVO monthly(Integer year) {
        int y = year != null ? year : LocalDate.now().getYear();
        LocalDate from = LocalDate.of(y, 1, 1);
        LocalDate to = LocalDate.of(y, 12, 31);

        // 按月索引聚合后补齐 12 个月（无发生额月份补 0）
        Map<Integer, MonthlyRawVO> salesByMonth = toMap(invQueryMapper.selectSalesMonthly(from, to));
        Map<Integer, MonthlyRawVO> purchaseByMonth = toMap(invQueryMapper.selectPurchaseMonthly(from, to));

        List<MonthlyStatVO.MonthRow> rows = IntStream.rangeClosed(1, 12)
                .mapToObj(m -> {
                    MonthlyRawVO s = salesByMonth.get(m);
                    MonthlyRawVO p = purchaseByMonth.get(m);
                    return new MonthlyStatVO.MonthRow(m,
                            s == null ? BigDecimal.ZERO : nvl(s.totalSum()),
                            s == null ? BigDecimal.ZERO : nvl(s.taxSum()),
                            p == null ? BigDecimal.ZERO : nvl(p.totalSum()),
                            p == null ? BigDecimal.ZERO : nvl(p.taxSum()));
                })
                .toList();
        return new MonthlyStatVO(y, rows);
    }

    @Override
    public ReconciliationVO reconciliation(String partnerType, Long partnerId, LocalDate from, LocalDate to) {
        if (!"customer".equals(partnerType) && !"supplier".equals(partnerType)) {
            throw BusinessException.ofKey("erp.invoice.partner-type-invalid", partnerType);
        }
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate begin = from != null ? from : end.minusYears(1);

        boolean customer = "customer".equals(partnerType);
        BizStatRawVO biz = customer
                ? invQueryMapper.sumCustomerOutstock(partnerId, begin, end)
                : invQueryMapper.sumSupplierInstock(partnerId, begin, end);
        BizStatRawVO invoiced = customer
                ? invQueryMapper.sumSalesInvoiced(partnerId, begin, end)
                : invQueryMapper.sumPurchaseInvoiced(partnerId, begin, end);

        Map<Long, String> names = customer
                ? partnerNameLookup.customerNames(List.of(partnerId))
                : partnerNameLookup.supplierNames(List.of(partnerId));

        BigDecimal bizAmount = nvl(biz.amountSum());
        BigDecimal invoicedAmount = nvl(invoiced.amountSum());
        return new ReconciliationVO(partnerType, partnerId, names.get(partnerId), begin, end,
                biz.cnt() == null ? 0 : biz.cnt(), bizAmount,
                invoiced.cnt() == null ? 0 : invoiced.cnt(), invoicedAmount,
                bizAmount.subtract(invoicedAmount));
    }

    // ==================== 私有方法 ====================

    /** 月度原始行按月份数字建索引 */
    private Map<Integer, MonthlyRawVO> toMap(List<MonthlyRawVO> rows) {
        Map<Integer, MonthlyRawVO> index = new HashMap<>(16);
        for (MonthlyRawVO row : rows) {
            if (row.month() != null) {
                index.put(row.month(), row);
            }
        }
        return index;
    }

    /** SUM 结果空值兜底为 0 */
    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
