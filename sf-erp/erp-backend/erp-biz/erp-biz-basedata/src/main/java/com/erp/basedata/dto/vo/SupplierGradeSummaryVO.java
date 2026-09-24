package com.erp.basedata.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 供应商评级汇总报表 VO（V141）：指定周期的等级分布 + 均值 + 总交易额。
 */
@Data
public class SupplierGradeSummaryVO {

    /** 周期类型 MONTH/QUARTER */
    private String periodType;

    /** 周期标识 yyyy-MM / yyyy-Qn */
    private String period;

    /** 参与评分供应商数 */
    private long supplierCount;

    /** A/B/C/D 各级数量 */
    private long gradeA;
    private long gradeB;
    private long gradeC;
    private long gradeD;

    /** 平均总分（无数据为 0） */
    private BigDecimal avgScore;

    /** 周期交易额合计 */
    private BigDecimal totalOrderAmount;
}
