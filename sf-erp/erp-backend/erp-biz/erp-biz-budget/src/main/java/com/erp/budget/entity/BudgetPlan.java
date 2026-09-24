package com.erp.budget.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预算方案主表实体（budget_plan）。
 * <p>
 * 方案 = 年度 + 部门×科目×12 个月明细矩阵（子表 {@link BudgetItem}）+ 控制配置
 * （{@link BudgetControlConfig}）。状态机单向流转：DRAFT 草稿(可编制/改/删) →
 * RELEASED 已下达(锁定禁改，参与预算控制与执行分析) → CLOSED 已关闭(仅留存档)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_plan")
public class BudgetPlan extends BaseEntity {

    /** 状态：草稿（可编制明细、修改、删除） */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 状态：已下达（明细锁定禁改，参与控制与执行分析） */
    public static final String STATUS_RELEASED = "RELEASED";
    /** 状态：已关闭（留档，不再参与控制） */
    public static final String STATUS_CLOSED = "CLOSED";

    /** 方案编码（全局唯一，建议 YS+年度+序号，如 YS2026-001） */
    private String planCode;

    /** 方案名称 */
    private String planName;

    /** 预算年度（2000-2100） */
    private Integer budgetYear;

    /** 状态：DRAFT/RELEASED/CLOSED */
    private String status;

    /** 预算总额（明细保存时冗余重算） */
    private BigDecimal totalAmount;

    /** 编制人（sys_user.id） */
    private Long prepareBy;

    /** 备注 */
    private String remark;

    /** 创建时间（列表展示用） */
    private LocalDateTime createTime;
}
