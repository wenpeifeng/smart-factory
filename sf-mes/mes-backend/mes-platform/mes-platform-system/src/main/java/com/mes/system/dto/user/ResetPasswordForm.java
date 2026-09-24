package com.mes.system.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码表单（管理员重置他人密码）。
 */
@Data
public class ResetPasswordForm {

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度须在 6-20 位之间")
    private String password;
}
