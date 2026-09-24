package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * BOM 子件明细 —— 一行代表“单件父件耗用多少子件”。
 * <p>
 * 关键约束：qty &gt; 0；loss_rate 取值 [0,100]。
 * MRP 展开公式：子件毛需求 = 父件净需求 × qty × (1 + loss_rate/100)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_bom_item")
public class MrpBomItem extends BaseEntity implements Serializable {

    /** BOM头ID(mrp_bom.id) */
    private Long bomId;

    /** 子件物料ID(base_material.id) */
    private Long materialId;

    /** 单位用量（单件成品耗用子件数量） */
    private BigDecimal qty;

    /** 损耗率% [0,100] */
    private BigDecimal lossRate;

    /** 备注 */
    private String remark;
}
