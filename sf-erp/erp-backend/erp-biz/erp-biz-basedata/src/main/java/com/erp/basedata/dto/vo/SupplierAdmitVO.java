package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.SupplierAdmit;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商准入申请 VO（V141）。
 */
@Data
public class SupplierAdmitVO {

    private Long id;
    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private String creditCode;
    private String contact;
    private String phone;
    private String address;
    private String licenseFile;
    private String certFiles;
    private String bankInfo;
    private String applyReason;
    /** 准入状态：0草稿 10待审 20通过 30驳回 40黑名单 */
    private Integer status;
    private Integer wfStarted;
    private Long auditBy;
    private LocalDateTime auditTime;
    private String auditRemark;
    private LocalDateTime blacklistTime;
    private String blacklistReason;
    private String remark;
    private LocalDateTime createTime;

    public static SupplierAdmitVO from(SupplierAdmit e) {
        SupplierAdmitVO vo = new SupplierAdmitVO();
        vo.setId(e.getId());
        vo.setSupplierId(e.getSupplierId());
        vo.setSupplierName(e.getSupplierName());
        vo.setCreditCode(e.getCreditCode());
        vo.setContact(e.getContact());
        vo.setPhone(e.getPhone());
        vo.setAddress(e.getAddress());
        vo.setLicenseFile(e.getLicenseFile());
        vo.setCertFiles(e.getCertFiles());
        vo.setBankInfo(e.getBankInfo());
        vo.setApplyReason(e.getApplyReason());
        vo.setStatus(e.getStatus());
        vo.setWfStarted(e.getWfStarted());
        vo.setAuditBy(e.getAuditBy());
        vo.setAuditTime(e.getAuditTime());
        vo.setAuditRemark(e.getAuditRemark());
        vo.setBlacklistTime(e.getBlacklistTime());
        vo.setBlacklistReason(e.getBlacklistReason());
        vo.setRemark(e.getRemark());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
