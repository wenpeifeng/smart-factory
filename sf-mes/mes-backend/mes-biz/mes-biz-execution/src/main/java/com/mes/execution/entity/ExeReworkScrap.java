package com.mes.execution.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 返工/报废单实体（表 exe_rework_scrap）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("exe_rework_scrap")
public class ExeReworkScrap extends BaseEntity {
    /** 单号（FG 返工 / BF 报废 + yyyyMMdd + 4位流水） */
    private String orderNo;
    /** 类型：REWORK 返工 / SCRAP 报废 */
    private String type;
    /** 工单 ID */
    private Long workorderId;
    /** 工单号（快照） */
    private String woNo;
    /** 工单工序 ID */
    private Long woStepId;
    /** 工序名称（快照） */
    private String operationName;
    /** 产品 SN（SN 模式） */
    private String sn;
    /** 数量 */
    private BigDecimal qty;
    /** 原因（必填） */
    private String reason;
    /** 关联检验单不合格处置 ID（qc_reject.id） */
    private Long qcRejectId;
    /** 状态：OPEN 待处理 / CLOSED 已处理 */
    private String status;
    /** 处理说明 */
    private String handleRemark;
    /** 处理时间 */
    private LocalDateTime handleTime;
}
