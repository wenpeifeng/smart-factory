package com.erp.hr.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 计件工资按员工汇总 VO。
 */
@Data
public class PieceWageEmployeeSummaryVO {

    /** 员工ID */
    private Long employeeId;

    /** 工号 */
    private String empNo;

    /** 员工姓名 */
    private String employeeName;

    /** 明细条数 */
    private Long detailCount;

    /** 完成数量合计 */
    private BigDecimal totalQty;

    /** 计件金额合计 */
    private BigDecimal totalAmount;
}
