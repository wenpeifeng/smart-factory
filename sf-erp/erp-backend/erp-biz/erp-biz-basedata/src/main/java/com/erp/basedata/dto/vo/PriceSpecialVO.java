package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.SalPriceSpecial;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 特价审批 VO（冗余客户/物料名称由 Service 填充）。
 */
@Data
public class PriceSpecialVO {

    /** 主键 */
    private Long id;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码（冗余） */
    private String customerCode;

    /** 客户名称（冗余） */
    private String customerName;

    /** 客户等级（冗余） */
    private String customerLevel;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码（冗余） */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 特价（不含税） */
    private BigDecimal specialPrice;

    /** 申请理由 */
    private String applyReason;

    /** 生效起（含，空=不限） */
    private LocalDate validFrom;

    /** 生效止（含，空=不限） */
    private LocalDate validTo;

    /** 状态：0待审 10通过 20驳回 */
    private Integer status;

    /** 是否走 workflow 审批：0-管理员审批 1-审批流待办 */
    private Integer wfStarted;

    /** 审批人（0=工作流自动通过） */
    private Long auditBy;

    /** 审批时间 */
    private LocalDateTime auditTime;

    /** 审批意见 */
    private String auditRemark;

    /** 备注 */
    private String remark;

    /** 申请时间 */
    private LocalDateTime createTime;

    /**
     * 从实体转换。
     *
     * @param entity 实体
     * @return VO
     */
    public static PriceSpecialVO from(SalPriceSpecial entity) {
        PriceSpecialVO vo = new PriceSpecialVO();
        vo.setId(entity.getId());
        vo.setCustomerId(entity.getCustomerId());
        vo.setMaterialId(entity.getMaterialId());
        vo.setSpecialPrice(entity.getSpecialPrice());
        vo.setApplyReason(entity.getApplyReason());
        vo.setValidFrom(entity.getValidFrom());
        vo.setValidTo(entity.getValidTo());
        vo.setStatus(entity.getStatus());
        vo.setWfStarted(entity.getWfStarted());
        vo.setAuditBy(entity.getAuditBy());
        vo.setAuditTime(entity.getAuditTime());
        vo.setAuditRemark(entity.getAuditRemark());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
