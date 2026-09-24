package com.erp.integration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 集成出站发件箱（int_outbox）—— ERP→MES 下推的可靠投递载体。
 * <p>事务内先落 PENDING，投递成功置 SUCCESS；失败按指数退避
 * （1m/5m/30m/2h/6h）重试 5 次后置 DEAD 死信，管理端可视 + 手动重试。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("int_outbox")
public class IntOutbox extends BaseEntity {

    /** 业务类型：PRODUCTION_ORDER / MATERIAL / BOM / ROUTING */
    private String bizType;

    /** 业务单据 ID（关联来源表主键） */
    private Long bizId;

    /** 事件时间（入队时刻，幂等键成分） */
    private LocalDateTime eventTime;

    /** 报文 JSON 快照（重试时反序列化重发） */
    private String payload;

    /** 状态：PENDING 待发 / SUCCESS 成功 / DEAD 死信 */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间（指数退避，PENDING 且到期才被重试 Job 捞取） */
    private LocalDateTime nextRetryTime;

    /** 最近一次错误摘要（500 字截断） */
    private String error;

    /** 成功时间 */
    private LocalDateTime successTime;
}
