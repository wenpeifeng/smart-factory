package com.erp.integration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 集成入站消息日志（int_message_log）—— MES→ERP 回传的幂等载体。
 * <p>唯一键 (biz_type, biz_key, event_time)：重复消息直接返回成功并标记 DUPLICATE；
 * 业务处理与消息日志在同一事务（先占位后处理，整体回滚不留脏数据）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("int_message_log")
public class IntMessageLog extends BaseEntity {

    /** 业务类型：WORK_REPORT / PICKING / RETURN / QUALITY / ORDER_STATUS */
    private String bizType;

    /** 业务幂等键（报工=woNo|工序，领料=pickNo，退料=returnNo，质检=inspectNo，状态=woNo|mesStatus|事件时间） */
    private String bizKey;

    /** 事件时间（报工时间等，幂等键成分） */
    private LocalDateTime eventTime;

    /** 报文 JSON 快照 */
    private String payload;

    /** 状态：SUCCESS 已处理 / DUPLICATE 重复跳过 */
    private String status;

    /** 处理结果摘要 / 错误信息 */
    private String error;
}
