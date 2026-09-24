package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 供应商准入申请实体（表 base_supplier_admit，V141）。
 * <p>
 * 流程：登记（0草稿，同时创建 base_supplier status=0 停用）→ 提交申请（10待审，
 * 自动尝试挂 workflow bizType=supplier_admit，无流程转管理员简单审批）→
 * 通过（20，base_supplier 启用）/ 驳回（30，可改后重提）/ 黑名单（40，
 * base_supplier.is_blacklist=1，采购下单/审核拦截）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_supplier_admit")
public class SupplierAdmit extends BaseEntity {

    /** 状态：草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 状态：待审 */
    public static final int STATUS_PENDING = 10;
    /** 状态：通过 */
    public static final int STATUS_APPROVED = 20;
    /** 状态：驳回 */
    public static final int STATUS_REJECTED = 30;
    /** 状态：黑名单 */
    public static final int STATUS_BLACKLIST = 40;

    /** 供应商 ID（base_supplier.id，登记时创建） */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 联系人 */
    private String contact;

    /** 手机号 */
    private String phone;

    /** 地址 */
    private String address;

    /** 营业执照附件（文件标识/URL） */
    private String licenseFile;

    /** 其他资质附件（逗号分隔多文件） */
    private String certFiles;

    /** 开户行/账号（可选） */
    private String bankInfo;

    /** 申请说明 */
    private String applyReason;

    /** 准入状态：0草稿 10待审 20通过 30驳回 40黑名单 */
    private Integer status;

    /** 是否已发起 workflow 审批：0-否（管理员审批） 1-是（待办审批，惰性同步结果） */
    private Integer wfStarted;

    /** 审批人（0=工作流自动通过） */
    private Long auditBy;

    /** 审批时间 */
    private LocalDateTime auditTime;

    /** 审批意见 */
    private String auditRemark;

    /** 拉黑时间 */
    private LocalDateTime blacklistTime;

    /** 拉黑原因 */
    private String blacklistReason;

    /** 备注 */
    private String remark;
}
