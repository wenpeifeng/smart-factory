package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 生产领料明细 —— should_qty = BOM用量 × 订单数量 × (1+损耗率)；
 * actual_qty 由用户确认，累计实领不允许超过应领（审核时校验）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_material_issue_item")
public class MrpMaterialIssueItem extends BaseEntity implements Serializable {

    /** 领料单ID(mrp_material_issue.id) */
    private Long issueId;

    /** 子件物料ID */
    private Long materialId;

    /** 主料物料ID（替代料倒冲行记录对应主料，主料行自身为 NULL，V621） */
    private Long mainMaterialId;

    /** 应领数量 */
    private BigDecimal shouldQty;

    /** 实领数量 */
    private BigDecimal actualQty;
}
