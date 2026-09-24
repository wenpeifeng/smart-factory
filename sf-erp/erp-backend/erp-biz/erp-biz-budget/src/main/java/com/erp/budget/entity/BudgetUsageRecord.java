package com.erp.budget.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预算执行数登记实体（budget_usage_record）—— 推式记账。
 * <p>
 * 用途：fin_payment 等约定表【没有部门/科目维度】，付款类预算执行数无法按表直接
 * 归属到 部门×科目×期间。约定由业务发生方（finance 模块付款单创建/审核）调用
 * {@code BudgetControlService.record(...)} 落一条记录（source_type=PAYMENT）；
 * 冲销/调整传负数金额。凭证类执行数不需要登记——由 fin_voucher×fin_voucher_entry
 * 按制单人部门只读汇总（见 CrossModuleMapper）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_usage_record")
public class BudgetUsageRecord extends BaseEntity {

    /** 来源类型：付款（finance 付款单接线写入 / 演示接口写入） */
    public static final String SOURCE_PAYMENT = "PAYMENT";
    /** 来源类型：凭证（预留，凭证口径当前走只读汇总不落记录） */
    public static final String SOURCE_VOUCHER = "VOUCHER";
    /** 来源类型：手工登记/调整 */
    public static final String SOURCE_MANUAL = "MANUAL";

    /** 部门 ID（sys_dept.id） */
    private Long deptId;

    /** 科目 ID（fin_subject.id，费用/成本类） */
    private Long subjectId;

    /** 年度 */
    private Integer year;

    /** 月份（1-12） */
    private Integer month;

    /** 发生金额（冲销传负数） */
    private BigDecimal amount;

    /** 来源类型：PAYMENT/VOUCHER/MANUAL */
    private String sourceType;

    /** 来源单据 ID（如 fin_payment.id） */
    private Long sourceId;

    /** 来源单据号（如 FK202609050001） */
    private String sourceNo;

    /** 业务日期 */
    private LocalDate bizDate;

    /** 备注 */
    private String remark;
}
