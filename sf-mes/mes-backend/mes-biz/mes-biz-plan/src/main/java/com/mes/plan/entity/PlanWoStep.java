package com.mes.plan.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单工序任务实体（表 plan_wo_step，派工/过站报工的对象）。
 * <p>状态机：PENDING_DISPATCH → DISPATCHED → PROCESSING → FINISHED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("plan_wo_step")
public class PlanWoStep extends BaseEntity {
    /** 工单 ID（plan_workorder.id） */
    private Long workorderId;
    /** 序号（工序顺序） */
    private Integer seqNo;
    /** 工序 ID（base_operation.id） */
    private Long operationId;
    /** 工序名称（快照） */
    private String operationName;
    /** 工作中心 ID（base_workcenter.id） */
    private Long workcenterId;
    /** 单件工时分钟（工艺路线快照） */
    private BigDecimal unitMinutes;
    /** 准备时间分钟（快照） */
    private Integer setupMinutes;
    /** 计划数量（=工单数量） */
    private BigDecimal planQty;
    /** 计划开始时间 */
    private LocalDateTime planStartTime;
    /** 计划结束时间 */
    private LocalDateTime planEndTime;
    /** 状态：PENDING_DISPATCH/DISPATCHED/PROCESSING/FINISHED */
    private String status;
    /** 派工对象用户 ID（sys_user.id） */
    private Long assigneeId;
    /** 派工对象姓名（快照） */
    private String assigneeName;
    /** 派工时间 */
    private LocalDateTime dispatchTime;
    /** 累计过站合格数 */
    private BigDecimal finishQty;
    /** 累计报废数 */
    private BigDecimal scrapQty;
    /** 累计返工数 */
    private BigDecimal reworkQty;
    /** 累计实际工时（分钟） */
    private Integer actualMinutes;
    /** 是否质检点（IPQC 挂点）：1 是 / 0 否 */
    private Integer qcFlag;
}
