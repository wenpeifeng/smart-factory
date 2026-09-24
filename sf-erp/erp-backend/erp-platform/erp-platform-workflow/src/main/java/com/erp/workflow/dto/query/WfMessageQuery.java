package com.erp.workflow.dto.query;

import lombok.Data;

/**
 * 消息中心分页查询条件。
 */
@Data
public class WfMessageQuery {

    private long pageNum = 1;
    private long pageSize = 10;

    /** 消息类型：TODO/APPROVE/REJECT/WARN */
    private String msgType;

    /** 仅未读：true 时只返回未读消息 */
    private Boolean unreadOnly;
}
