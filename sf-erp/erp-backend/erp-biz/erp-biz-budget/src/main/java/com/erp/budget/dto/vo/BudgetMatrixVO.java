package com.erp.budget.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 预算明细矩阵 VO —— 按部门×科目分行、12 个月分列，附行/列合计。
 * <p>
 * 用于编制页可编辑表格渲染与保存回显（保存整体提交 rows）。
 */
@Data
public class BudgetMatrixVO {

    /** 方案 ID */
    private Long planId;

    /** 预算年度 */
    private Integer budgetYear;

    /** 方案状态（前端据 DRAFT 与否禁编） */
    private String status;

    /** 矩阵行（部门×科目） */
    private List<MatrixRowVO> rows = new ArrayList<>();

    /** 列合计（m1..m12 + 全年总计） */
    private ColumnTotalVO columnTotals = new ColumnTotalVO();

    /**
     * 矩阵行：部门 × 科目 × 12 个月金额 + 行合计。
     */
    @Data
    public static class MatrixRowVO {

        /** 部门 ID */
        private Long deptId;

        /** 部门名称（sys_dept.name 只读关联） */
        private String deptName;

        /** 科目 ID */
        private Long subjectId;

        /** 科目编码（fin_subject.code） */
        private String subjectCode;

        /** 科目名称 */
        private String subjectName;

        /** 1-12 月预算金额（空视为 0） */
        private BigDecimal m1;
        private BigDecimal m2;
        private BigDecimal m3;
        private BigDecimal m4;
        private BigDecimal m5;
        private BigDecimal m6;
        private BigDecimal m7;
        private BigDecimal m8;
        private BigDecimal m9;
        private BigDecimal m10;
        private BigDecimal m11;
        private BigDecimal m12;

        /** 行合计（该部门×科目全年预算） */
        private BigDecimal rowTotal;
    }

    /**
     * 列合计：各月全年合计 + 总计。
     */
    @Data
    public static class ColumnTotalVO {

        private BigDecimal m1 = BigDecimal.ZERO;
        private BigDecimal m2 = BigDecimal.ZERO;
        private BigDecimal m3 = BigDecimal.ZERO;
        private BigDecimal m4 = BigDecimal.ZERO;
        private BigDecimal m5 = BigDecimal.ZERO;
        private BigDecimal m6 = BigDecimal.ZERO;
        private BigDecimal m7 = BigDecimal.ZERO;
        private BigDecimal m8 = BigDecimal.ZERO;
        private BigDecimal m9 = BigDecimal.ZERO;
        private BigDecimal m10 = BigDecimal.ZERO;
        private BigDecimal m11 = BigDecimal.ZERO;
        private BigDecimal m12 = BigDecimal.ZERO;

        /** 全方案全年总计 */
        private BigDecimal grandTotal = BigDecimal.ZERO;
    }
}
