package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 费用分析 VO（部门 × 科目 借方发生额）。
 * <p>
 * 数据源：POSTED 凭证分录，科目 = 损益类+成本类借方（5001/5101/6401/6601/6602/6603 等）；
 * 部门 = 凭证制单人所属部门（fin_voucher 无部门列的口径降级，无部门用户归「未分配部门」）。
 */
@Data
public class ExpenseVO {

    /** 期间 yyyy-MM */
    private String period;

    /** 部门 × 科目行（按部门、金额降序） */
    private List<Row> rows;

    /** 借方发生额合计（元） */
    private BigDecimal totalAmount;

    /** 单行费用 */
    @Data
    public static class Row {

        /** 部门 ID（0 = 未分配） */
        private Long deptId;

        /** 部门名称（未分配部门） */
        private String deptName;

        private String subjectCode;

        private String subjectName;

        /** 借方发生额（元） */
        private BigDecimal amount;

        /** 占费用合计比例（0~1，4 位 HALF_UP；合计 0 → 0） */
        private BigDecimal ratio;
    }
}
