package com.mes.notify.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知发送日志实体（notify_send_log）。
 * <p>
 * 每次外发（含测试发送）重试收口后落一行：成功/失败、Markdown 全文、耗时。
 * 日志表只增不改，按建表规范保留 deleted 列（恒 0）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_send_log")
public class NotifySendLog extends BaseEntity {

    /** 事件类型：待办推送 */
    public static final String EVENT_TODO = "TODO";
    /** 事件类型：审批结果 */
    public static final String EVENT_APPROVAL = "APPROVAL";
    /** 事件类型：预警 */
    public static final String EVENT_WARN = "WARN";
    /** 事件类型：测试发送 */
    public static final String EVENT_TEST = "TEST";

    /** 通道 ID（notify_channel_config.id） */
    private Long channelId;

    /** 通道类型：webhook/dingtalk/wecom */
    private String channelType;

    /** 事件类型：TODO/APPROVAL/WARN/TEST */
    private String eventType;

    /** 接收人用户 ID（群机器人无接收人时 NULL） */
    private Long receiverId;

    /** 接收人外部标识（webhook 域名/钉钉 userid/企微 userid/手机号） */
    private String receiver;

    /** 消息标题 */
    private String title;

    /** 发送的 Markdown 内容 */
    private String content;

    /** 发送结果：1 成功 0 失败 */
    private Integer success;

    /** 失败原因 */
    private String errorMsg;

    /** 耗时毫秒（含重试） */
    private Long costMs;
}
