package com.erp.basedata.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 供应商新增/修改表单（字段与客户基本对称，期初字段为应付）。
 * <p>编码 code 可留空，留空时按「GYS+日期+3位流水」自动生成。
 */
@Data
public class BaseSupplierForm {

    /** 供应商编码（留空自动生成） */
    @Size(max = 40, message = "供应商编码长度不能超过40")
    private String code;

    /** 供应商名称 */
    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 100, message = "供应商名称长度不能超过100")
    private String name;

    /** 联系人 */
    @Size(max = 50, message = "联系人长度不能超过50")
    private String contact;

    /** 手机号（11 位数字，可为空） */
    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    /** 地址 */
    @Size(max = 200, message = "地址长度不能超过200")
    private String address;

    /** 统一社会信用代码（18 位，可为空） */
    @Pattern(regexp = "^$|[0-9A-HJ-NPQRTUWXY]{18}", message = "统一社会信用代码应为18位")
    private String creditCode;

    /** 结算方式 ID */
    private Long settleTypeId;

    /** 期初应付，不能为负 */
    @DecimalMin(value = "0", message = "期初应付不能为负数")
    private BigDecimal initPayable;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
