package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 委外发料明细 —— qty = BOM用量 × 委外数量 × (1+损耗率)，创建草稿时自动带出。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_outsource_item")
public class MrpOutsourceItem extends BaseEntity implements Serializable {

    /** 委外单ID(mrp_outsource.id) */
    private Long outsourceId;

    /** 发料子件物料ID */
    private Long materialId;

    /** 发料数量 */
    private BigDecimal qty;
}
