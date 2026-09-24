package com.mes.integration.authcenter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * auth-center 用户 DTO（GET /service/apps/{clientId}/users 实际响应字段）。
 * <p>匹配键 = employeeNo（工号，不可变）；active=false（LEFT 离职 / SUSPENDED 全局停用 /
 * APP_DISABLED 应用级禁用）时本地副本软删除（status=0）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthCenterUserDTO {
    /** 工号（不可变，本地 sys_user.username 的同步匹配键） */
    private String employeeNo;
    /** 登录名（默认同工号） */
    private String username;
    /** 姓名 */
    private String realName;
    /** 组织名称 */
    private String orgName;
    /** 岗位 */
    private String position;
    /** 在职且有权进入本应用 */
    private Boolean active;
    /** 失效原因：LEFT / SUSPENDED / APP_DISABLED */
    private String inactiveReason;
    /** 角色标签（如 mes.admin = 本系统超管） */
    private List<String> roles;
    /** 最近变更时间（增量同步水位） */
    private String updateTime;
}
