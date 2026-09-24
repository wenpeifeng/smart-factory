package com.erp.basedata.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 供应商准入登记/编辑表单（V141）。
 * <p>
 * id 为空 = 新登记（服务端自动创建 base_supplier，status=0 停用，编码自动生成）；
 * submit=true 时登记后立即提交申请（0→10），否则存草稿。
 */
@Data
public class SupplierAdmitForm {

    /** 申请 ID（编辑时传） */
    private Long id;

    /** 供应商名称 */
    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 100, message = "供应商名称最长 100 字")
    private String supplierName;

    /** 统一社会信用代码 */
    @Size(max = 30, message = "统一社会信用代码最长 30 字")
    private String creditCode;

    /** 联系人 */
    @Size(max = 50, message = "联系人最长 50 字")
    private String contact;

    /** 手机号 */
    @Size(max = 20, message = "手机号最长 20 字")
    private String phone;

    /** 地址 */
    @Size(max = 200, message = "地址最长 200 字")
    private String address;

    /** 营业执照附件（文件标识/URL） */
    @Size(max = 500, message = "营业执照附件标识最长 500 字")
    private String licenseFile;

    /** 其他资质附件（逗号分隔多文件） */
    @Size(max = 1000, message = "资质附件标识最长 1000 字")
    private String certFiles;

    /** 开户行/账号（可选） */
    @Size(max = 200, message = "开户信息最长 200 字")
    private String bankInfo;

    /** 申请说明 */
    @Size(max = 200, message = "申请说明最长 200 字")
    private String applyReason;

    /** 备注 */
    @Size(max = 200, message = "备注最长 200 字")
    private String remark;

    /** 是否登记后立即提交申请（默认 false 存草稿） */
    private boolean submit;
}
