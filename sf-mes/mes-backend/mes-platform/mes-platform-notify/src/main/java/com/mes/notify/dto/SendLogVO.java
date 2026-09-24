package com.mes.notify.dto;

import com.mes.notify.entity.NotifySendLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知发送日志 VO。
 */
@Data
public class SendLogVO {

    /** 日志 ID */
    private Long id;

    /** 通道 ID */
    private Long channelId;

    /** 通道类型：webhook/dingtalk/wecom */
    private String channelType;

    /** 事件类型：TODO/APPROVAL/WARN/TEST */
    private String eventType;

    /** 接收人用户 ID */
    private Long receiverId;

    /** 接收人外部标识 */
    private String receiver;

    /** 消息标题 */
    private String title;

    /** Markdown 内容 */
    private String content;

    /** 发送结果：1 成功 0 失败 */
    private Integer success;

    /** 失败原因 */
    private String errorMsg;

    /** 耗时毫秒 */
    private Long costMs;

    /** 发送时间 */
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param log 日志实体
     * @return VO
     */
    public static SendLogVO from(NotifySendLog log) {
        SendLogVO vo = new SendLogVO();
        vo.setId(log.getId());
        vo.setChannelId(log.getChannelId());
        vo.setChannelType(log.getChannelType());
        vo.setEventType(log.getEventType());
        vo.setReceiverId(log.getReceiverId());
        vo.setReceiver(log.getReceiver());
        vo.setTitle(log.getTitle());
        vo.setContent(log.getContent());
        vo.setSuccess(log.getSuccess());
        vo.setErrorMsg(log.getErrorMsg());
        vo.setCostMs(log.getCostMs());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
