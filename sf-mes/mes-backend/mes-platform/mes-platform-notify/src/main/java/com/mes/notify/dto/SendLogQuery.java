package com.mes.notify.dto;

import com.mes.framework.mybatis.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知发送日志分页查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SendLogQuery extends PageQuery {

    /** 通道 ID（某通道的发送记录 Drawer） */
    private Long channelId;

    /** 通道类型：webhook/dingtalk/wecom */
    private String channelType;

    /** 事件类型：TODO/APPROVAL/WARN/TEST */
    private String eventType;

    /** 发送结果：1 成功 0 失败 */
    private Integer success;
}
