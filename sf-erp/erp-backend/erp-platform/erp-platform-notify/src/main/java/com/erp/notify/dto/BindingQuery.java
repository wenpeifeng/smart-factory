package com.erp.notify.dto;

import com.erp.framework.mybatis.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户绑定分页查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BindingQuery extends PageQuery {

    /** 用户 ID 精确过滤 */
    private Long userId;

    /** 通道：dingtalk/wecom */
    private String channel;

    /** 外部标识（手机号/userid）模糊匹配 */
    private String externalId;
}
