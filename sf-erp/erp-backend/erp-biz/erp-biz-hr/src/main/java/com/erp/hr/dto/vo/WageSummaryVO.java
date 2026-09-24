package com.erp.hr.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工资汇总 VO —— 计件合计 + 加班费 − 缺勤扣款（仅计算汇总，不生成凭证）。
 */
@Data
public class WageSummaryVO {

    /** 员工ID */
    private Long employeeId;

    /** 工号 */
    private String empNo;

    /** 员工姓名 */
    private String employeeName;

    /** 部门名称 */
    private String deptName;

    /** 月薪基准 */
    private BigDecimal baseSalary;

    /** 计件合计（hr_piece_wage_detail 当期合计） */
    private BigDecimal pieceAmount;

    /** 加班天数（当月 OVERTIME 考勤天数） */
    private Integer overtimeDays;

    /** 加班费 = 加班天数 × 50 */
    private BigDecimal overtimePay;

    /** 缺勤天数（当月 ABSENT 考勤天数） */
    private Integer absentDays;

    /** 缺勤扣款 = 月薪基准 / 30 × 缺勤天数 */
    private BigDecimal absentDeduct;

    /** 应发合计 = 计件 + 加班费 − 缺勤扣款 */
    private BigDecimal totalAmount;
}
