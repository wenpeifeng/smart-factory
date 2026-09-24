package com.erp.budget.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算明细矩阵保存表单（整体提交：行 = 部门×科目，列 = 12 个月金额）。
 * <p>
 * 未出现在 rows 里的部门×科目组合将被逻辑删除；已出现的按单元格 upsert。
 */
@Data
public class BudgetMatrixForm {

    /** 矩阵行集合 */
    @Valid
    @NotNull(message = "预算明细行不能为空")
    private List<MatrixRowForm> rows;

    /**
     * 矩阵行：部门 × 科目 × 12 个月金额。
     */
    @Data
    public static class MatrixRowForm {

        /** 部门 ID（sys_dept.id） */
        @NotNull(message = "部门不能为空")
        private Long deptId;

        /** 科目 ID（fin_subject.id，限末级费用/成本类） */
        @NotNull(message = "科目不能为空")
        private Long subjectId;

        /** 1 月金额 */
        @DecimalMin(value = "0", message = "预算金额不能为负数")
        private BigDecimal m1;
        /** 2 月金额 */
        @DecimalMin(value = "0", message = "预算金额不能为负数")
        private BigDecimal m2;
        /** 3 月金额 */
        @DecimalMin(value = "0", message = "预算金额不能为负数")
        private BigDecimal m3;
        /** 4 月金额 */
        @DecimalMin(value = "0", message = "预算金额不能为负数")
        private BigDecimal m4;
        /** 5 月金额 */
        @DecimalMin(value = "0", message = "预算金额不能为负数")
        private BigDecimal m5;
        /** 6 月金额 */
        @DecimalMin(value = "0", message = "预算金额不能为负数")
        private BigDecimal m6;
        /** 7 月金额 */
        @DecimalMin(value = "0", message = "预算金额不能为负数")
        private BigDecimal m7;
        /** 8 月金额 */
        @DecimalMin(value = "0", message = "预算金额不能为负数")
        private BigDecimal m8;
        /** 9 月金额 */
        @DecimalMin(value = "0", message = "预算金额不能为负数")
        private BigDecimal m9;
        /** 10 月金额 */
        @DecimalMin(value = "0", message = "预算金额不能为负数")
        private BigDecimal m10;
        /** 11 月金额 */
        @DecimalMin(value = "0", message = "预算金额不能为负数")
        private BigDecimal m11;
        /** 12 月金额 */
        @DecimalMin(value = "0", message = "预算金额不能为负数")
        private BigDecimal m12;
    }
}
