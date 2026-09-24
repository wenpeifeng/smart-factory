package com.erp.mrp.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 工艺路线复制新版本表单。
 */
@Data
public class RoutingCopyForm implements Serializable {

    /** 新版本号 */
    @NotBlank(message = "新版本号不能为空")
    @Size(max = 20, message = "新版本号最长20字符")
    private String newVersion;
}
