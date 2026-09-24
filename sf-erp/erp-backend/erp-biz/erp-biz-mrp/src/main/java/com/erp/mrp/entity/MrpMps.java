package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 主生产计划 MPS —— MRP 运算的需求来源。
 * <p>
 * 关键约束：material 必须存在有效(ACTIVE)BOM 才能参与运算；
 * 已运算(CALCULATED)的计划行不会再次被“全量待运算”模式拾取。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_mps")
public class MrpMps extends BaseEntity implements Serializable {

    /** 成品物料ID(base_material.id) */
    private Long materialId;

    /** 计划生产数量 */
    private BigDecimal qty;

    /** 计划开工日期 */
    private LocalDate startDate;

    /** 状态：PENDING待运算 CALCULATED已运算 */
    private String status;

    /** 备注 */
    private String remark;
}
