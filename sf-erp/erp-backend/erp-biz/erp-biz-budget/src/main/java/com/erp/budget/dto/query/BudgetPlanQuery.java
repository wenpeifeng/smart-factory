package com.erp.budget.dto.query;

import lombok.Data;

/**
 * 预算方案分页查询条件。
 */
@Data
public class BudgetPlanQuery {

    private long pageNum = 1;
    private long pageSize = 10;

    /** 关键词：方案编码/名称模糊 */
    private String keyword;

    /** 预算年度 */
    private Integer budgetYear;

    /** 状态：DRAFT/RELEASED/CLOSED */
    private String status;
}
