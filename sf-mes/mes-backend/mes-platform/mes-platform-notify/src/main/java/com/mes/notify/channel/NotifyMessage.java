package com.mes.notify.channel;

/**
 * 通知消息载体 —— NotifyService 组装后交通道发送的统一负载。
 * <p>
 * 通道无关：title/summary 供卡片标题，markdown 为正文（钉钉/企微 Markdown 语法），
 * link 为移动审批链接（待办事件携带），receiverIdentify 为通道侧解析出的外部标识
 * （钉钉 userid / 企微 userid；群机器人不使用）。
 */
public class NotifyMessage {

    /** 事件类型：TODO/APPROVAL/WARN/TEST（见 NotifySendLog.EVENT_*） */
    private final String eventType;
    /** 接收人用户 ID（群机器人无接收人时 null） */
    private final Long receiverUserId;
    /** 消息标题（卡片 title / 列表页标题） */
    private final String title;
    /** Markdown 正文 */
    private final String markdown;
    /** 移动审批链接（仅待办事件） */
    private final String link;
    /** 通道侧外部标识（钉钉/企微 userid；群机器人 null） */
    private final String receiverIdentify;

    public NotifyMessage(String eventType, Long receiverUserId, String title,
                         String markdown, String link, String receiverIdentify) {
        this.eventType = eventType;
        this.receiverUserId = receiverUserId;
        this.title = title;
        this.markdown = markdown;
        this.link = link;
        this.receiverIdentify = receiverIdentify;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getReceiverUserId() {
        return receiverUserId;
    }

    public String getTitle() {
        return title;
    }

    public String getMarkdown() {
        return markdown;
    }

    public String getLink() {
        return link;
    }

    public String getReceiverIdentify() {
        return receiverIdentify;
    }
}
