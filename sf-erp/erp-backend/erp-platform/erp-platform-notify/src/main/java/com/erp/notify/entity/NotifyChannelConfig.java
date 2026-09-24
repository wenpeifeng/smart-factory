package com.erp.notify.entity;

import com.erp.framework.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知通道配置实体（notify_channel_config）。
 * <p>
 * 通道类型（channel_type）与凭据结构（credentials JSON）：
 * <ul>
 *   <li>webhook：群机器人（钉钉/企微通用），凭据 {@code {"webhookUrl":"https://..."}} —— 交付即可真实测试；</li>
 *   <li>dingtalk：企业内部应用，凭据 {@code {"corpId","agentId","appSecret","aesKey?","token?"}}；</li>
 *   <li>wecom：企微自建应用，凭据 {@code {"corpId","agentId","secret","token?","encodingAesKey?"}}。</li>
 * </ul>
 * 事件订阅 event_mask 位映射见 {@link #MASK_TODO} 等常量。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_channel_config")
public class NotifyChannelConfig extends BaseEntity {

    /** 通道类型：webhook 群机器人 */
    public static final String TYPE_WEBHOOK = "webhook";
    /** 通道类型：钉钉企业内部应用 */
    public static final String TYPE_DINGTALK = "dingtalk";
    /** 通道类型：企微自建应用 */
    public static final String TYPE_WECOM = "wecom";

    /** 事件位：待办推送（TaskCreatedEvent） */
    public static final int MASK_TODO = 1;
    /** 事件位：审批结果（ApprovalResultEvent） */
    public static final int MASK_APPROVAL = 2;
    /** 事件位：预警（WarnEvent） */
    public static final int MASK_WARN = 4;
    /** 全部事件 */
    public static final int MASK_ALL = MASK_TODO | MASK_APPROVAL | MASK_WARN;

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;

    /** 通道类型：webhook/dingtalk/wecom */
    private String channelType;

    /** 通道名称（展示用） */
    private String channelName;

    /** 凭据 JSON（结构按通道类型，接口返回一律脱敏） */
    private String credentials;

    /** 事件订阅 bitmask：bit0 待办 / bit1 审批结果 / bit2 预警 */
    private Integer eventMask;

    /** ERP 移动端基地址，推送链接 = mobileBaseUrl + /m/approve/{taskId} */
    private String mobileBaseUrl;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 通道是否订阅指定事件位 */
    public boolean subscribes(int mask) {
        return eventMask != null && (eventMask & mask) != 0;
    }
}
