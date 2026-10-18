package com.erp.workflow.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 消息中心实体（wf_message）。
 * <p>
 * 审批流转（新待办/通过/驳回）与预警任务自动写入；接收人维度未读计数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfMessage extends BaseEntity {

    /** 类型：新待办 */
    public static final String TYPE_TODO = "TODO";
    /** 类型：审批通过 */
    public static final String TYPE_APPROVE = "APPROVE";
    /** 类型：审批驳回 */
    public static final String TYPE_REJECT = "REJECT";
    /** 类型：预警 */
    public static final String TYPE_WARN = "WARN";
    /** 认证中心人员变更。 */
    public static final String TYPE_IDENTITY = "IDENTITY";

    /** 未读标志：未读 */
    public static final int UNREAD = 0;
    /** 未读标志：已读 */
    public static final int READ = 1;

    /** 接收人用户 ID（sys_user.id） */
    private Long receiverId;

    /** 消息类型：TODO/APPROVE/REJECT/WARN */
    private String msgType;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 关联前端路径（点击跳转，如 /workflow/todo） */
    private String bizPath;

    /** 已读标志：0未读 1已读 */
    private Integer readFlag;

    /** 已读时间 */
    private LocalDateTime readTime;
}
