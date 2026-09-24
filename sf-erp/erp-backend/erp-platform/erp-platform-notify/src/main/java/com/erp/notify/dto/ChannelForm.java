package com.erp.notify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 通知通道新增/修改表单。
 * <p>
 * 凭据按通道类型用平铺字段承载，服务端组装 credentials JSON：
 * <ul>
 *   <li>webhook：webhookUrl 必填；</li>
 *   <li>dingtalk：corpId/agentId/appSecret 必填，aesKey/token 可选（回调验签）；</li>
 *   <li>wecom：corpId/agentId/secret 必填，aesKey(=encodingAesKey)/token 可选（回调验签）。</li>
 * </ul>
 * 修改时凭据字段传空或掩码串（****开头）表示"保持原值不变"。
 */
@Data
public class ChannelForm {

    /** 通道类型：webhook/dingtalk/wecom */
    @NotBlank(message = "通道类型不能为空")
    private String channelType;

    /** 通道名称 */
    @NotBlank(message = "通道名称不能为空")
    @Size(max = 60, message = "通道名称最长 60 字")
    private String channelName;

    /** webhook 地址（webhook 类型必填） */
    @Size(max = 500, message = "webhook 地址最长 500 字")
    private String webhookUrl;

    /** 企业 ID（钉钉/企微） */
    @Size(max = 100, message = "corpId 最长 100 字")
    private String corpId;

    /** 应用 agentId（钉钉/企微） */
    @Size(max = 50, message = "agentId 最长 50 字")
    private String agentId;

    /** 钉钉应用密钥 AppSecret */
    @Size(max = 200, message = "appSecret 最长 200 字")
    private String appSecret;

    /** 企微应用密钥 Secret */
    @Size(max = 200, message = "secret 最长 200 字")
    private String secret;

    /** 回调验签 token（钉钉/企微可选） */
    @Size(max = 200, message = "token 最长 200 字")
    private String token;

    /** 回调加解密密钥（钉钉 aesKey / 企微 EncodingAESKey，可选） */
    @Size(max = 100, message = "aesKey 最长 100 字")
    private String aesKey;

    /** 事件订阅 bitmask：1 待办 / 2 审批结果 / 4 预警（null=7 全部） */
    private Integer eventMask;

    /** ERP 移动端基地址（推送链接 = 该地址 + /m/approve/{taskId}） */
    @Size(max = 200, message = "移动端地址最长 200 字")
    private String mobileBaseUrl;

    /** 状态：1 启用 0 停用（null 按启用） */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注最长 200 字")
    private String remark;
}
