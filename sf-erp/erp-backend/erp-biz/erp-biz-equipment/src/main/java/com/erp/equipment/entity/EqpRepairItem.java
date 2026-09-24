package com.erp.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 维修工单备件明细实体（行）。
 * <p>
 * cost_ref 为完工时点快照：数量 × base_material.ref_cost（HALF_UP 保留 2 位），
 * 只作成本参考，不参与库存计价。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eqp_repair_item")
public class EqpRepairItem extends BaseEntity {

    /** 工单 ID（eqp_repair.id） */
    private Long repairId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 使用数量（DECIMAL(18,4)） */
    private BigDecimal qty;

    /** 成本参考（数量 × 物料参考成本快照，DECIMAL(18,2)） */
    private BigDecimal costRef;
}
