package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工序报工明细 —— 一次报工一行，事务内回写 {@link MrpWoStep} 累计数与状态。
 * <p>
 * QC 挂点：bad_qty &gt; 0 时在 qc_note 记录质量待处理说明，
 * QC 模块上线后可按 wo_step_id / work_order_id 消费本表数据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_work_report")
public class MrpWorkReport extends BaseEntity implements Serializable {

    /** 工单工序任务ID(mrp_wo_step.id) */
    private Long woStepId;

    /** 生产订单ID（冗余，便于按订单追溯） */
    private Long workOrderId;

    /** 工序ID（冗余） */
    private Long operationId;

    /** 报工员工姓名 */
    private String employee;

    /** 合格数 */
    private BigDecimal goodQty;

    /** 不合格数 */
    private BigDecimal badQty;

    /** 实际工时（分钟） */
    private Integer workMinutes;

    /** 质量待处理说明（QC 挂点） */
    private String qcNote;

    /** 报工时间 */
    private LocalDateTime reportTime;
}
