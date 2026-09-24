package com.erp.notify.dto;

import com.erp.notify.entity.NotifyChannelConfig;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

/**
 * 通知通道响应 VO —— 凭据一律脱敏（仅末 4 位可见），并给出已配置的凭据键清单。
 */
@Data
public class ChannelVO {

    /** 通道 ID */
    private Long id;

    /** 通道类型：webhook/dingtalk/wecom */
    private String channelType;

    /** 通道名称 */
    private String channelName;

    /** 脱敏凭据（键 → ****尾4位；webhookUrl 保留前缀协议+host） */
    private Map<String, String> credentials;

    /** 已配置的凭据键（前端编辑时判断哪些已填） */
    private java.util.List<String> credentialKeys;

    /** 事件订阅 bitmask：bit0 待办 / bit1 审批结果 / bit2 预警 */
    private Integer eventMask;

    /** ERP 移动端基地址 */
    private String mobileBaseUrl;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 实体转 VO（凭据脱敏）。
     *
     * @param entity 通道实体
     * @return 脱敏 VO
     */
    public static ChannelVO from(NotifyChannelConfig entity) {
        ChannelVO vo = new ChannelVO();
        vo.setId(entity.getId());
        vo.setChannelType(entity.getChannelType());
        vo.setChannelName(entity.getChannelName());
        vo.setEventMask(entity.getEventMask());
        vo.setMobileBaseUrl(entity.getMobileBaseUrl());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setCredentials(new TreeMap<>());
        vo.setCredentialKeys(new java.util.ArrayList<>());
        return vo;
    }
}
