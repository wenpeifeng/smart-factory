package com.erp.workflow.service;

import com.erp.common.result.PageResult;
import com.erp.workflow.dto.query.WfMessageQuery;
import com.erp.workflow.dto.vo.WfMessageVO;
import com.erp.workflow.entity.WfMessage;

/**
 * 消息中心服务 —— 审批通知与预警消息的写入/查询/已读。
 */
public interface WfMessageService {

    /**
     * 写入一条消息（审批流转/预警任务调用）。
     *
     * @param receiverId 接收人用户 ID
     * @param msgType    消息类型：TODO/APPROVE/REJECT/WARN（见 {@link WfMessage} 常量）
     * @param title      标题
     * @param content    内容（可空）
     * @param bizPath    关联前端路径（可空，如 /workflow/todo）
     */
    void push(Long receiverId, String msgType, String title, String content, String bizPath);

    /**
     * 我的消息分页。
     *
     * @param query 查询条件（类型/仅未读）
     * @return 消息分页
     */
    PageResult<WfMessageVO> pageMine(WfMessageQuery query);

    /**
     * 标记单条消息已读（仅接收人本人）。
     *
     * @param id 消息 ID
     * @throws com.erp.common.exception.BusinessException 消息不存在/非本人消息
     */
    void markRead(Long id);

    /**
     * 我的全部未读消息标记已读。
     *
     * @return 本次标记条数
     */
    int markAllRead();

    /**
     * 我的未读消息数。
     *
     * @return 未读条数
     */
    long unreadCount();
}
