package com.mes.plan.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 齐套分析快照明细实体（表 plan_kit_check_item）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("plan_kit_check_item")
public class PlanKitCheckItem extends BaseEntity {
    /** 齐套分析 ID（plan_kit_check.id） */
    private Long checkId;
    /** 物料 ID */
    private Long materialId;
    /** 需求数量 */
    private BigDecimal reqQty;
    /** 可用数量（线边仓 + ERP 可用） */
    private BigDecimal availableQty;
    /** 缺口数量（>0 为缺料） */
    private BigDecimal shortQty;
    /** 行结果：OK 齐套 / SHORT 缺料 */
    private String result;
}
