package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单工序任务 VO（派工/报工看板行；含订单号、成品、工序、工作中心回显与进度）。
 */
@Data
public class WoStepVO implements Serializable {

    /** 工序任务ID */
    private Long id;

    /** 生产订单ID */
    private Long workOrderId;

    /** 生产订单号 */
    private String orderNo;

    /** 成品物料编码 */
    private String materialCode;

    /** 成品物料名称 */
    private String materialName;

    /** 序号 */
    private Integer seqNo;

    /** 工序ID */
    private Long operationId;

    /** 工序编码 */
    private String operationCode;

    /** 工序名称 */
    private String operationName;

    /** 工作中心ID */
    private Long workCenterId;

    /** 工作中心编码 */
    private String workCenterCode;

    /** 工作中心名称 */
    private String workCenterName;

    /** 单件工时（分钟/件，快照） */
    private BigDecimal unitMinutes;

    /** 计划数量 */
    private BigDecimal planQty;

    /** 累计合格数 */
    private BigDecimal finishQty;

    /** 累计不合格数 */
    private BigDecimal badQty;

    /** 合格进度%（合格/计划，超过100按100展示由前端处理） */
    private BigDecimal progress;

    /** 计划开始时间 */
    private LocalDateTime planStartTime;

    /** 计划结束时间 */
    private LocalDateTime planEndTime;

    /** 状态：PENDING_DISPATCH/DISPATCHED/PROCESSING/FINISHED */
    private String status;

    /** 派工对象 */
    private String assignee;

    /** 派工时间 */
    private LocalDateTime dispatchTime;

    /** 累计实际工时（分钟） */
    private Integer actualMinutes;

    /** 质量待处理说明（QC 挂点） */
    private String qcPendingNote;
}
