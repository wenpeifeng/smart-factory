package com.erp.budget.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 预算明细实体（budget_item）—— 方案×部门×科目×月份 矩阵中的一个单元格。
 * <p>
 * 部门取 sys_dept（约定表），科目取 fin_subject 且限【末级费用/成本类】
 * （direction=DEBIT 且 category IN (COST, PROFIT_LOSS) 且无子科目）。
 * 唯一键 (plan_id, dept_id, subject_id, month)：保存矩阵走 upsert
 * （ON DUPLICATE KEY 顺带复活逻辑删行），禁止删后重插以免撞唯一键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_item")
public class BudgetItem extends BaseEntity {

    /** 方案 ID（budget_plan.id） */
    private Long planId;

    /** 部门 ID（sys_dept.id） */
    private Long deptId;

    /** 科目 ID（fin_subject.id，限末级费用/成本类） */
    private Long subjectId;

    /** 月份（1-12） */
    private Integer month;

    /** 预算金额（>=0） */
    private BigDecimal amount;
}
