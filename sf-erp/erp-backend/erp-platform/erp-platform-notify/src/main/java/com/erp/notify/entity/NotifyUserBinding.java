package com.erp.notify.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户外部账号绑定实体（notify_user_binding）。
 * <p>
 * 钉钉/企微通道按此把 ERP 用户映射到外部账号：
 * 钉钉存手机号（通道调 getbymobile 换 userid），企微存成员 userid（通常配置为手机号）。
 * webhook 群机器人不需要绑定。未绑定用户该通道跳过并降级站内信。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_user_binding")
public class NotifyUserBinding extends BaseEntity {

    /** ERP 用户 ID（sys_user.id） */
    private Long userId;

    /** 外部通道：dingtalk/wecom */
    private String channel;

    /** 外部标识：钉钉=手机号 / 企微=成员userid */
    private String externalId;
}
