package com.erp.workflow.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息中心 VO。
 */
@Data
public class WfMessageVO {

    /** 消息 ID */
    private Long id;

    /** 接收人 ID */
    private Long receiverId;

    /** 消息类型：TODO/APPROVE/REJECT/WARN */
    private String msgType;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 关联前端路径 */
    private String bizPath;

    /** 已读标志：0未读 1已读 */
    private Integer readFlag;

    /** 已读时间 */
    private LocalDateTime readTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
