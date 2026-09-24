package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商交易评分实体（表 pur_supplier_score，V141）。
 * <p>
 * 月度/季度周期对各供应商的交易表现加权评分：
 * 总分 = 100 × (30%×准时交货率 + 40%×来料合格率 + 30%×(1-退货率))；
 * 等级 A≥90 / B≥75 / C≥60 / D&lt;60。重算为「先删后插」，uk(supplier,periodType,period) 兜底。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pur_supplier_score")
public class PurSupplierScore extends BaseEntity {

    /** 供应商 ID（base_supplier.id） */
    private Long supplierId;

    /** 周期类型：MONTH 月度 / QUARTER 季度 */
    private String periodType;

    /** 周期标识：yyyy-MM / yyyy-Qn */
    private String period;

    /** 周期起（含） */
    private LocalDate periodStart;

    /** 周期止（含） */
    private LocalDate periodEnd;

    /** 期间已审核订单数 */
    private Integer orderCount;

    /** 期间交易额（订单价税合计） */
    private BigDecimal orderAmount;

    /** 期间已审核入库单数 */
    private Integer instockCount;

    /** 期间入库数量 */
    private BigDecimal instockQty;

    /** 准时交货率（%） */
    private BigDecimal ontimeRate;

    /** 来料合格率（%） */
    private BigDecimal qualifiedRate;

    /** 退货率（%） */
    private BigDecimal returnRate;

    /** 加权总分（0-100） */
    private BigDecimal totalScore;

    /** 等级：A/B/C/D */
    private String grade;

    /** 计算时间 */
    private LocalDateTime calcTime;
}
