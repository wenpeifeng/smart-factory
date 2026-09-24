package com.erp.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 工单成本归集 —— 「料工费」三费中的手工录入部分 + 生产订单快照。
 * <p>
 * 料（直接材料）与工（直接人工）不入本表，查询时实时归集：
 * 料 = mrp_material_issue(_item) 已审核领料量 × CostService 移动加权单价；
 * 工 = hr_piece_wage_detail(work_order_id, employee_id, amount) 计件工资（HR 约定表，
 * 未建成前优雅降级为 0）。费（制造费用）= 本表 overhead_amount / labor_hours 手工分摊。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_work_order")
public class CostWorkOrder extends BaseEntity {

    /** 生产订单 ID（mrp_work_order.id，唯一） */
    private Long workOrderId;

    /** 生产订单号快照（MO…） */
    private String orderNo;

    /** 成品物料 ID（base_material.id） */
    private Long materialId;

    /** 订单数量快照 */
    private BigDecimal qty;

    /** 完工数量快照 */
    private BigDecimal finishQty;

    /** 订单状态快照（DRAFT/RELEASED/PRODUCING/FINISHED/CLOSED） */
    private String status;

    /** 工时（小时，制造费用分摊依据，手工录入） */
    private BigDecimal laborHours;

    /** 制造费用分摊金额（手工录入） */
    private BigDecimal overheadAmount;

    /** 备注 */
    private String remark;
}
