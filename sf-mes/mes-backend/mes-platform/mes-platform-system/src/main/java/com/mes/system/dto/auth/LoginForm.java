package com.mes.system.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求表单。
 */
@Data
public class LoginForm {

    /** 登录账号 */
    @NotBlank(message = "账号不能为空")
    private String username;

    /** 登录密码（明文，仅 HTTPS 传输） */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 租户 ID（可选：登录页选择租户后传入；缺省按默认租户 1000 定位账号） */
    private Long tenantId;
}
