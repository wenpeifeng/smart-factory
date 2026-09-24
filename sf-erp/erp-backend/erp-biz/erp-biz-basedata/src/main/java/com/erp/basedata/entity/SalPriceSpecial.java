package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 特价审批实体（表 sal_price_special，V131）。
 * <p>
 * 申请（0待审）→ 审批（workflow bizType=sal_price_special，存在启用流程则走待办，
 * 否则管理员简单审批）→ 通过（10）后进入定价引擎<b>最高优先级</b>，
 * 受生效期 [validFrom, validTo]（空=不限）约束；驳回为 20。
 * wfStarted=1 的记录由服务层对 workflow.isApproved 做惰性同步收敛为 10。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_price_special")
public class SalPriceSpecial extends BaseEntity {

    /** 状态：待审 */
    public static final int STATUS_PENDING = 0;
    /** 状态：通过 */
    public static final int STATUS_APPROVED = 10;
    /** 状态：驳回 */
    public static final int STATUS_REJECTED = 20;

    /** 客户 ID（base_customer.id） */
    private Long customerId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 特价（不含税），DECIMAL(18,4) */
    private BigDecimal specialPrice;

    /** 申请理由 */
    private String applyReason;

    /** 生效起（含，空=不限） */
    private LocalDate validFrom;

    /** 生效止（含，空=不限） */
    private LocalDate validTo;

    /** 状态：0待审 10通过 20驳回 */
    private Integer status;

    /** 是否已发起 workflow 审批：0-否（管理员审批） 1-是（待办审批） */
    private Integer wfStarted;

    /** 审批人（0=工作流自动通过） */
    private Long auditBy;

    /** 审批时间 */
    private LocalDateTime auditTime;

    /** 审批意见 */
    private String auditRemark;

    /** 备注 */
    private String remark;
}
