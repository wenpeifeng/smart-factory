package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按日销售统计行 VO（GET /sales/statistics/daily）。
 * <p>
 * 口径：仅统计"已审核"销售出库单；成本按 base_material.ref_cost × 数量计算，
 * 毛利 = 销售额 − 成本。日期无数据的日期不返回行（前端自行补零展示）。
 */
@Data
public class SalDailyStatVO {

    /** 统计日期 yyyy-MM-dd */
    private String statDate;

    /** 销售额（已审核出库单金额合计） */
    private BigDecimal salesAmount;

    /** 销售成本（ref_cost × 数量 合计） */
    private BigDecimal costAmount;

    /** 毛利 = salesAmount - costAmount */
    private BigDecimal profitAmount;

    /** 出库数量合计 */
    private BigDecimal totalQty;
}
