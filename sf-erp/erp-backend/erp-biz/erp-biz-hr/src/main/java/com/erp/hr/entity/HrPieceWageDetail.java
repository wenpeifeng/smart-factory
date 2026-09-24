package com.erp.hr.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 计件工资明细 —— 归集自完工生产订单，行内快照订单号/工序/单价保证可追溯。
 * <p>
 * ★ erp-cost 协作契约：本表必含 (work_order_id, employee_id, amount) 三列，
 * cost 模块按 work_order_id 分组 SUM(amount) 归集工单人工成本，不 import 本模块代码。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_piece_wage_detail")
public class HrPieceWageDetail extends BaseEntity implements Serializable {

    /** 期间ID（hr_piece_wage.id） */
    private Long periodId;

    /** 员工ID（hr_employee.id） */
    private Long employeeId;

    /** 生产订单ID（mrp_work_order.id，来源可追溯；uk(period_id, work_order_id) 防重复归集） */
    private Long workOrderId;

    /** 生产订单号快照（归集时冗余，防源单号后续变更） */
    private String workOrderNo;

    /** 物料ID（base_material.id） */
    private Long materialId;

    /** 工序名称（取价目表） */
    private String processName;

    /** 完成数量（取订单累计完工良品数） */
    private BigDecimal qty;

    /** 单价（取价目表） */
    private BigDecimal unitPrice;

    /** 金额 = 数量 × 单价（2位 HALF_UP） */
    private BigDecimal amount;
}
