package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MRP 发补建议 —— 运算的净需求按“物料+建议类型”汇总后的结果。
 * <p>
 * plan_type 判定：物料存在有效 BOM → PRODUCE（继续展开子级）；
 * 无 BOM → PURCHASE（采购件叶子）。PURCHASE 的转单是采购模块集成点，
 * 本模块只实现 PRODUCE 转生产订单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_suggestion")
public class MrpSuggestion extends BaseEntity implements Serializable {

    /** 运算批次号(mrp_calc.batch_no) */
    private String batchNo;

    /** 建议类型：PURCHASE采购建议 PRODUCE生产建议 */
    private String planType;

    /** 物料ID */
    private Long materialId;

    /** 建议数量 */
    private BigDecimal qty;

    /** 建议日期（=需求日期−提前期7天） */
    private LocalDate suggestDate;

    /** BOM层级（取该物料首次出现层级） */
    private Integer level;

    /** 状态：PENDING待处理 CONVERTED已转单 */
    private String status;

    /** 转单引用类型：WORK_ORDER */
    private String refType;

    /** 转单引用ID */
    private Long refId;
}
