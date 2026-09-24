package com.erp.hr.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 考勤月度汇总 VO —— 按员工汇总当月各状态天数。
 */
@Data
public class AttendanceSummaryVO {

    /** 员工ID */
    private Long employeeId;

    /** 工号 */
    private String empNo;

    /** 员工姓名 */
    private String employeeName;

    /** 部门名称 */
    private String deptName;

    /** 正常天数 */
    private Integer normalDays;

    /** 缺勤天数 */
    private Integer absentDays;

    /** 迟到天数 */
    private Integer lateDays;

    /** 请假天数 */
    private Integer leaveDays;

    /** 加班天数 */
    private Integer overtimeDays;

    /** 出勤天数合计（正常+迟到，与状态口径一致：到岗即出勤） */
    private BigDecimal attendedDays;
}
