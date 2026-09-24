package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工序报工明细 VO。
 */
@Data
public class WorkReportVO implements Serializable {

    /** 报工ID */
    private Long id;

    /** 工单工序任务ID */
    private Long woStepId;

    /** 生产订单ID */
    private Long workOrderId;

    /** 生产订单号 */
    private String orderNo;

    /** 工序ID */
    private Long operationId;

    /** 工序名称 */
    private String operationName;

    /** 报工员工姓名 */
    private String employee;

    /** 合格数 */
    private BigDecimal goodQty;

    /** 不合格数 */
    private BigDecimal badQty;

    /** 实际工时（分钟） */
    private Integer workMinutes;

    /** 质量待处理说明 */
    private String qcNote;

    /** 报工时间 */
    private LocalDateTime reportTime;
}
