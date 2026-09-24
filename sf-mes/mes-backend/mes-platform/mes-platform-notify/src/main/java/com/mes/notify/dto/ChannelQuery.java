package com.mes.notify.dto;

import com.mes.framework.mybatis.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知通道分页查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChannelQuery extends PageQuery {

    /** 关键词：通道名称模糊匹配 */
    private String keyword;

    /** 通道类型：webhook/dingtalk/wecom */
    private String channelType;

    /** 状态过滤：1-启用 0-停用 */
    private Integer status;
}
