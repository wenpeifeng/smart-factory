package com.erp.notify.core;

import com.erp.common.result.PageResult;
import com.erp.notify.dto.BindingForm;
import com.erp.notify.dto.BindingQuery;
import com.erp.notify.dto.BindingVO;
import com.erp.notify.dto.ChannelForm;
import com.erp.notify.dto.ChannelQuery;
import com.erp.notify.dto.ChannelVO;
import com.erp.notify.dto.SendLogQuery;
import com.erp.notify.dto.SendLogVO;

/**
 * 统一通知服务 —— 事件路由/降级 + 通道配置管理 + 发送日志 + 用户绑定。
 * <p>
 * 事件侧（workflow publish → 本模块监听）：
 * <ul>
 *   <li>按事件类型路由到"启用且订阅该事件"的通道（webhook/dingtalk/wecom）；</li>
 *   <li>失败自动重试 1 次，仍失败写发送日志并降级 wf_message 站内信 + log.warn；</li>
 *   <li>钉钉/企微按 notify_user_binding 找外部账号，未绑定该通道跳过并降级站内信。</li>
 * </ul>
 */
public interface NotifyService {

    /* ===================== 通道配置 ===================== */

    /** 通道分页 */
    PageResult<ChannelVO> pageChannels(ChannelQuery query);

    /** 新增通道（按类型校验必填凭据） */
    Long createChannel(ChannelForm form);

    /** 修改通道（凭据字段传空/掩码串=保持原值） */
    void updateChannel(Long id, ChannelForm form);

    /** 删除通道（逻辑删） */
    void deleteChannel(Long id);

    /** 启用/停用通道 */
    void changeChannelStatus(Long id, int status);

    /** 测试发送：经指定通道发一条测试 Markdown（钉钉/企微需指定接收人） */
    SendLogVO testSend(Long id, Long receiverUserId);

    /* ===================== 发送日志 ===================== */

    /** 发送日志分页 */
    PageResult<SendLogVO> pageSendLogs(SendLogQuery query);

    /* ===================== 用户绑定 ===================== */

    /** 绑定分页 */
    PageResult<BindingVO> pageBindings(BindingQuery query);

    /** 新增绑定（同用户同通道唯一，重复则更新） */
    Long createBinding(BindingForm form);

    /** 删除绑定 */
    void deleteBinding(Long id);
}
