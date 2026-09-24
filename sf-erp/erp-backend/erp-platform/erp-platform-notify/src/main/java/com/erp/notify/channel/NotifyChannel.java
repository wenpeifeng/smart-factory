package com.erp.notify.channel;

import com.erp.notify.entity.NotifyChannelConfig;

/**
 * 通知通道 SPI —— 所有外部推送通道实现本接口。
 * <p>
 * 实现注册为 Spring Bean，NotifyService 按 {@link #type()} 匹配
 * notify_channel_config.channel_type 路由。实现要求：
 * <ul>
 *   <li><b>不得抛异常</b>：网络/凭据/平台错误一律包装为 {@link NotifyResult} 失败返回，
 *       由 NotifyService 统一重试与降级；</li>
 *   <li><b>无状态</b>：全部凭据来自 config.credentials JSON，令牌类缓存放实现内部；</li>
 *   <li><b>超时收敛</b>：HTTP 超时 5s 内返回，不长时间阻塞调用线程。</li>
 * </ul>
 */
public interface NotifyChannel {

    /**
     * 通道类型标识（与 notify_channel_config.channel_type 一致）。
     */
    String type();

    /**
     * 发送一条通知。
     *
     * @param message 消息负载（markdown 已组装好）
     * @param config  通道配置（credentials JSON 内含凭据）
     * @return 发送结果（不抛异常）
     */
    NotifyResult send(NotifyMessage message, NotifyChannelConfig config);
}
