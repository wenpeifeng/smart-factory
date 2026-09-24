package com.erp.notify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户外部账号绑定表单。
 */
@Data
public class BindingForm {

    /** ERP 用户 ID */
    @NotNull(message = "用户不能为空")
    private Long userId;

    /** 外部通道：dingtalk/wecom */
    @NotBlank(message = "通道不能为空")
    @Pattern(regexp = "dingtalk|wecom", message = "通道仅支持 dingtalk/wecom")
    private String channel;

    /** 外部标识：钉钉=手机号 / 企微=成员 userid（通常为手机号） */
    @NotBlank(message = "外部标识不能为空")
    @Size(max = 64, message = "外部标识最长 64 字")
    private String externalId;
}
