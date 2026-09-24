package com.erp.report.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.report.dto.query.FinanceSummaryQuery;
import com.erp.report.dto.query.PurchaseDetailQuery;
import com.erp.report.dto.query.SalesDetailQuery;
import com.erp.report.dto.vo.FinanceSummaryVO;
import com.erp.report.dto.vo.InventoryAgingVO;
import com.erp.report.dto.vo.ReportRowVO;
import com.erp.report.mapper.ReportMapper;
import com.erp.report.service.ReportQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多维报表查询服务实现（跨模块只读聚合）。
 * <p>
 * 口径要点：
 * <ul>
 *   <li>销售/采购明细只统计已审核单据；日期区间半开 [from, to)；</li>
 *   <li>库龄锚点 = 仓+物料最后一次入库方向流水的 create_time，分桶在 Java 完成；</li>
 *   <li>财务营收/成本：期间有 POSTED 凭证取 6001/6401 科目汇总，否则降级业务口径。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReportQueryServiceImpl implements ReportQueryService {

    /** 库龄标准桶（顺序即展示顺序） */
    private static final String[][] AGING_BUCKETS = {
            {"0-30", "0-30天"},
            {"31-90", "31-90天"},
            {"91-180", "91-180天"},
            {"180+", "180天以上"},
    };
    /** 无入库流水兜底桶 */
    private static final String[] AGING_NONE = {"NONE", "无入库流水"};

    private final ReportMapper reportMapper;

    @Override
    public List<ReportRowVO> salesDetail(SalesDetailQuery query) {
        String groupBy = normalizeGroup(query.getGroupBy(), Set.of("material", "customer", "month"), "material");
        List<Map<String, Object>> rows = switch (groupBy) {
            case "customer" -> reportMapper.salesDetailByCustomer(query.getFrom(), query.getTo(), query.getCustomerId());
            case "month" -> reportMapper.salesDetailByMonth(query.getFrom(), query.getTo(), query.getCustomerId());
            default -> reportMapper.salesDetailByMaterial(query.getFrom(), query.getTo(), query.getCustomerId());
        };
        return rows.stream().map(ReportQueryServiceImpl::toRow).toList();
    }

    @Override
    public List<ReportRowVO> purchaseDetail(PurchaseDetailQuery query) {
        String groupBy = normalizeGroup(query.getGroupBy(), Set.of("material", "supplier", "month"), "material");
        List<Map<String, Object>> rows = switch (groupBy) {
            case "supplier" -> reportMapper.purchaseDetailBySupplier(query.getFrom(), query.getTo(), query.getSupplierId());
            case "month" -> reportMapper.purchaseDetailByMonth(query.getFrom(), query.getTo(), query.getSupplierId());
            default -> reportMapper.purchaseDetailByMaterial(query.getFrom(), query.getTo(), query.getSupplierId());
        };
        return rows.stream().map(ReportQueryServiceImpl::toRow).toList();
    }

    @Override
    public InventoryAgingVO inventoryAging() {
        List<Map<String, Object>> rows = reportMapper.inventoryAgingRows();

        List<InventoryAgingVO.AgingDetailVO> details = new ArrayList<>(rows.size());
        // 桶标识 → 数量（保持标准桶固定顺序 + 兜底桶按出现追加）
        Map<String, BigDecimal> bucketQty = new java.util.LinkedHashMap<>();
        for (String[] bucket : AGING_BUCKETS) {
            bucketQty.put(bucket[0], BigDecimal.ZERO);
        }
        BigDecimal totalQty = BigDecimal.ZERO;

        for (Map<String, Object> row : rows) {
            Integer ageDays = row.get("ageDays") instanceof Number n ? n.intValue() : null;
            String[] bucket = bucketOf(ageDays);
            BigDecimal qty = asDecimal(row.get("qty"));
            bucketQty.merge(bucket[0], qty, BigDecimal::add);
            totalQty = totalQty.add(qty);

            InventoryAgingVO.AgingDetailVO d = new InventoryAgingVO.AgingDetailVO();
            d.setWarehouseId(asLong(row.get("warehouseId")));
            d.setWarehouseName(asString(row.get("warehouseName")));
            d.setMaterialId(asLong(row.get("materialId")));
            d.setMaterialCode(asString(row.get("materialCode")));
            d.setMaterialName(asString(row.get("materialName")));
            d.setQty(qty);
            d.setPrice(asDecimal(row.get("price")));
            d.setValue(asDecimal(row.get("value")));
            d.setLastInTime(asString(row.get("lastInTime")));
            d.setAgeDays(ageDays);
            d.setBucket(bucket[0]);
            d.setBucketLabel(bucket[1]);
            details.add(d);
        }

        InventoryAgingVO vo = new InventoryAgingVO();
        vo.setTotalQty(totalQty);
        final BigDecimal total = totalQty; // lambda 内引用需最终变量
        List<InventoryAgingVO.AgingBucketVO> buckets = new ArrayList<>();
        bucketQty.forEach((key, qty) -> {
            // 兜底桶只在确实存在无流水库存时展示，避免空数据时多一行噪音
            if (AGING_NONE[0].equals(key) && qty.signum() == 0) {
                return;
            }
            InventoryAgingVO.AgingBucketVO b = new InventoryAgingVO.AgingBucketVO();
            b.setBucket(key);
            b.setLabel(labelOf(key));
            b.setQty(qty);
            b.setRatio(total.signum() == 0 ? BigDecimal.ZERO
                    : qty.divide(total, 4, RoundingMode.HALF_UP));
            buckets.add(b);
        });
        vo.setBuckets(buckets);
        vo.setDetails(details);
        return vo;
    }

    @Override
    public FinanceSummaryVO financeSummary(FinanceSummaryQuery query) {
        YearMonth period = parsePeriod(query.getPeriod());
        LocalDate from = period.atDay(1);
        LocalDate to = period.plusMonths(1).atDay(1);

        FinanceSummaryVO vo = new FinanceSummaryVO();
        vo.setPeriod(period.toString());

        // 营收/成本：期间存在已过账凭证 → 总账科目汇总；否则业务口径降级
        boolean hasVoucher = reportMapper.countPostedVouchers(from, to) > 0;
        if (hasVoucher) {
            vo.setRevenue(reportMapper.sumVoucherSubjectAmount("CREDIT", "6001", from, to));
            vo.setCost(reportMapper.sumVoucherSubjectAmount("DEBIT", "6401", from, to));
            vo.setRevenueSource("VOUCHER");
            vo.setCostSource("VOUCHER");
        } else {
            vo.setRevenue(reportMapper.sumSalesAmount(from, to));
            vo.setCost(reportMapper.sumSalesRefCost(from, to));
            vo.setRevenueSource("BIZ");
            vo.setCostSource("BIZ");
        }
        vo.setGrossProfit(vo.getRevenue().subtract(vo.getCost()));
        vo.setGrossMargin(vo.getRevenue().signum() == 0 ? BigDecimal.ZERO
                : vo.getGrossProfit().divide(vo.getRevenue(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));

        vo.setReceivableBalance(reportMapper.sumReceivableBalance());
        vo.setPayableBalance(reportMapper.sumPayableBalance());

        Map<String, Object> flow = reportMapper.cashFlowTotal(from, to);
        BigDecimal cashIn = flow == null ? BigDecimal.ZERO : asDecimal(flow.get("inAmount"));
        BigDecimal cashOut = flow == null ? BigDecimal.ZERO : asDecimal(flow.get("outAmount"));
        vo.setCashIn(cashIn);
        vo.setCashOut(cashOut);
        vo.setCashNet(cashIn.subtract(cashOut));
        return vo;
    }

    // ==================== 私有工具 ====================

    /** groupBy 归一化：空 → 默认值；非法值 → 业务异常 */
    private String normalizeGroup(String groupBy, Set<String> allowed, String defaultVal) {
        if (groupBy == null || groupBy.isBlank()) {
            return defaultVal;
        }
        String value = groupBy.trim().toLowerCase();
        if (!allowed.contains(value)) {
            throw BusinessException.ofKey("erp.report.groupby-unsupported", String.join("/", allowed));
        }
        return value;
    }

    /** 期间解析：空 = 当前月；格式非法 → 业务异常 */
    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(period.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.ofKey("erp.report.period-format-ym", period);
        }
    }

    /** 库龄分桶：0-30 / 31-90 / 91-180 / 180+；无流水时间归入 NONE */
    private String[] bucketOf(Integer ageDays) {
        if (ageDays == null) {
            return AGING_NONE;
        }
        if (ageDays <= 30) {
            return AGING_BUCKETS[0];
        }
        if (ageDays <= 90) {
            return AGING_BUCKETS[1];
        }
        if (ageDays <= 180) {
            return AGING_BUCKETS[2];
        }
        return AGING_BUCKETS[3];
    }

    private String labelOf(String bucketKey) {
        for (String[] bucket : AGING_BUCKETS) {
            if (bucket[0].equals(bucketKey)) {
                return bucket[1];
            }
        }
        return AGING_NONE[1];
    }

    /** 聚合行 Map → ReportRowVO（数量/金额 BigDecimal 直传） */
    private static ReportRowVO toRow(Map<String, Object> row) {
        ReportRowVO vo = new ReportRowVO();
        vo.setDimCode(asString(row.get("dimCode")));
        vo.setDimName(asString(row.get("dimName")));
        vo.setQty(asDecimal(row.get("qty")));
        vo.setAmount(asDecimal(row.get("amount")));
        vo.setCostAmount(asNullableDecimal(row.get("costAmount")));
        vo.setProfitAmount(asNullableDecimal(row.get("profitAmount")));
        return vo;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static BigDecimal asDecimal(Object value) {
        BigDecimal d = asNullableDecimal(value);
        return d == null ? BigDecimal.ZERO : d;
    }

    private static BigDecimal asNullableDecimal(Object value) {
        if (value instanceof BigDecimal d) {
            return d;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return null;
    }
}
