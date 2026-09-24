package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单工序任务 —— 生产订单下达时按已审核工艺路线顺排生成的派工/报工对象。
 * <p>
 * 状态机：PENDING_DISPATCH待派工 → DISPATCHED已派工 → PROCESSING加工中(首次报工)
 * → FINISHED已完工(累计合格数达到计划数量)。
 * 计划时间顺排规则：首道工序计划开始 = 订单计划开工日 08:00（无计划开工日取当天），
 * 之后每道工序计划开始 = 前置工序计划结束，计划结束 = 计划开始 + 单件工时 × 订单数量。
 * qc_pending_note 为质量模块并行开发的挂点（不合格待处理说明）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_wo_step")
public class MrpWoStep extends BaseEntity implements Serializable {

    /** 生产订单ID(mrp_work_order.id) */
    private Long workOrderId;

    /** 序号（工序顺序） */
    private Integer seqNo;

    /** 工序ID(mrp_operation.id) */
    private Long operationId;

    /** 工作中心ID(mrp_work_center.id) */
    private Long workCenterId;

    /** 单件工时分钟（工艺路线快照） */
    private BigDecimal unitMinutes;

    /** 计划数量（=订单数量） */
    private BigDecimal planQty;

    /** 计划开始时间 */
    private LocalDateTime planStartTime;

    /** 计划结束时间 */
    private LocalDateTime planEndTime;

    /** 状态：PENDING_DISPATCH/DISPATCHED/PROCESSING/FINISHED */
    private String status;

    /** 派工对象（员工姓名或设备） */
    private String assignee;

    /** 派工时间 */
    private LocalDateTime dispatchTime;

    /** 累计报工合格数 */
    private BigDecimal finishQty;

    /** 累计报工不合格数 */
    private BigDecimal badQty;

    /** 累计实际工时（分钟） */
    private Integer actualMinutes;

    /** 质量待处理说明（不合格累计记录，QC 挂点） */
    private String qcPendingNote;
}
