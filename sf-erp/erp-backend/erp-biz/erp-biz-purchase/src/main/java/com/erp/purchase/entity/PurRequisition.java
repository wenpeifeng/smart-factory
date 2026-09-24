package com.erp.purchase.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 请购单主表实体（pur_requisition）。
 * <p>
 * 状态机（只允许正向流转）：0草稿 -> 10已提交 -> 20已转采购；
 * 10/20 可人工关闭为 30已关闭（终态）。修改/删除仅限草稿，提交仅限草稿。
 * 已转采购由询比价【定标】事务驱动（PurRfqServiceImpl#award），不在本实体对应表上手工改。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurRequisition extends BaseEntity {

    /** 状态：草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 状态：已提交 */
    public static final int STATUS_SUBMITTED = 10;
    /** 状态：已转采购（询比价定标回写） */
    public static final int STATUS_ORDERED = 20;
    /** 状态：已关闭（终态） */
    public static final int STATUS_CLOSED = 30;

    /** 请购单号（PR+yyyyMMdd+4位流水，Redis 生成，唯一） */
    private String reqNo;

    /** 请购部门 ID（sys_dept.id） */
    private Long deptId;

    /** 请购人 ID（sys_user.id，缺省当前登录用户） */
    private Long applicantId;

    /** 期望到货日期 */
    private LocalDate expectDate;

    /** 状态：0草稿 10已提交 20已转采购 30已关闭 */
    private Integer status;

    /** 备注 */
    private String remark;
}
