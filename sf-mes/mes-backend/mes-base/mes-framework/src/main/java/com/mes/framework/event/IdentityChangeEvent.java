package com.mes.framework.event;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 认证中心人员变更事件。集成模块发布，工作流模块转换为站内消息。
 */
public record IdentityChangeEvent(
        String changeType,
        String source,
        String employeeNo,
        String realName,
        String position,
        String phone,
        String email,
        LocalDate entryDate,
        LocalDateTime operationTime) {

    public static final String ASSIGNED = "ASSIGNED";
    public static final String LEFT = "LEFT";
    public static final String DISABLED = "DISABLED";
    public static final String ENABLED = "ENABLED";
    public static final String REVOKED = "REVOKED";
    public static final String SOURCE_GROUP = "GROUP";
    public static final String SOURCE_LOCAL = "LOCAL";
}
