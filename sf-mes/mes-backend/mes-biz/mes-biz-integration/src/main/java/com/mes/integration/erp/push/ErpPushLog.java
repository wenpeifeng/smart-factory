package com.mes.integration.erp.push;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * ERP 回传推送日志实体（表 int_push_log，V801）。
 * <p>幂等键：biz_type + biz_id（唯一索引）。STATUS：PENDING -> SUCCESS / FAILED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("int_push_log")
public class ErpPushLog extends BaseEntity {
    /** 业务类型：WORK_REPORT 完工报工 / PICKING 领料 / RETURN 退料 / QUALITY 质检结果 */
    private String bizType;
    /** 业务单据 id */
    private Long bizId;
    /** 业务单据号 */
    private String bizNo;
    /** 回传报文快照（JSON） */
    private String payload;
    /** 状态：PENDING / SUCCESS / FAILED */
    private String status;
    /** 已重试次数 */
    private Integer retryCount;
    /** 最大重试次数 */
    private Integer maxRetry;
    /** 最近一次失败原因 */
    private String lastError;
    /** 最近一次推送时间 */
    private LocalDateTime pushTime;
    /** 成功时间 */
    private LocalDateTime successTime;
}
