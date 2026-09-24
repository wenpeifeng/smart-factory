package com.mes.plan.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 工单用料需求实体（表 plan_wo_material，下达时按 BOM 展开；齐套分析对象）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("plan_wo_material")
public class PlanWoMaterial extends BaseEntity {
    /** 工单 ID（plan_workorder.id） */
    private Long workorderId;
    /** 子件物料 ID（base_material.id） */
    private Long materialId;
    /** 需求数量（=工单数量 x 单位用量 x (1+损耗率)） */
    private BigDecimal reqQty;
    /** 已领料数量（领料单审核回写） */
    private BigDecimal issuedQty;
}
