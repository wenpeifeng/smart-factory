package com.mes.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.PageResult;
import com.mes.workflow.dto.query.WfMessageQuery;
import com.mes.workflow.dto.vo.WfMessageVO;
import com.mes.workflow.entity.WfMessage;
import com.mes.workflow.mapper.WfMessageMapper;
import com.mes.workflow.security.CurrentUser;
import com.mes.workflow.service.WfMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息中心服务实现。
 * <p>
 * 已读更新全部走"接收人=当前登录人"条件，防止横向越权把他人消息标记已读。
 */
@Service
@RequiredArgsConstructor
public class WfMessageServiceImpl implements WfMessageService {

    private final WfMessageMapper messageMapper;

    @Override
    public void push(Long receiverId, String msgType, String title, String content, String bizPath) {
        WfMessage message = new WfMessage();
        message.setReceiverId(receiverId);
        message.setMsgType(msgType);
        message.setTitle(title);
        message.setContent(content);
        message.setBizPath(bizPath);
        message.setReadFlag(WfMessage.UNREAD);
        messageMapper.insert(message);
    }

    @Override
    public PageResult<WfMessageVO> pageMine(WfMessageQuery query) {
        Long userId = CurrentUser.requireUserId();
        var page = messageMapper.selectMessagePage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        query.getPageNum(), query.getPageSize()),
                userId, query.getMsgType(), query.getUnreadOnly());
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id) {
        Long userId = CurrentUser.requireUserId();
        // 条件更新：本人 + 未读才更新，天然幂等；影响 0 行说明不存在/已读/非本人
        int rows = messageMapper.update(null, Wrappers.lambdaUpdate(WfMessage.class)
                .eq(WfMessage::getId, id)
                .eq(WfMessage::getReceiverId, userId)
                .eq(WfMessage::getReadFlag, WfMessage.UNREAD)
                .set(WfMessage::getReadFlag, WfMessage.READ)
                .set(WfMessage::getReadTime, LocalDateTime.now()));
        if (rows == 0) {
            WfMessage message = messageMapper.selectById(id);
            if (message == null) {
                throw new BusinessException("消息[%d]不存在", id);
            }
            if (!message.getReceiverId().equals(userId)) {
                throw new BusinessException("无权操作他人的消息");
            }
            // 已读再点：幂等成功，不报错
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead() {
        Long userId = CurrentUser.requireUserId();
        List<WfMessage> unread = messageMapper.selectList(Wrappers.lambdaQuery(WfMessage.class)
                .eq(WfMessage::getReceiverId, userId)
                .eq(WfMessage::getReadFlag, WfMessage.UNREAD));
        if (unread.isEmpty()) {
            return 0;
        }
        messageMapper.update(null, Wrappers.lambdaUpdate(WfMessage.class)
                .eq(WfMessage::getReceiverId, userId)
                .eq(WfMessage::getReadFlag, WfMessage.UNREAD)
                .set(WfMessage::getReadFlag, WfMessage.READ)
                .set(WfMessage::getReadTime, LocalDateTime.now()));
        return unread.size();
    }

    @Override
    public long unreadCount() {
        Long userId = CurrentUser.requireUserId();
        Long count = messageMapper.selectCount(Wrappers.lambdaQuery(WfMessage.class)
                .eq(WfMessage::getReceiverId, userId)
                .eq(WfMessage::getReadFlag, WfMessage.UNREAD));
        return count == null ? 0L : count;
    }
}
