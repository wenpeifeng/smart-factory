package com.erp.hr.dto.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤明细 VO（带员工/部门回显）。
 */
@Data
public class AttendanceVO {

    private Long id;

    /** 员工ID */
    private Long employeeId;

    /** 工号 */
    private String empNo;

    /** 员工姓名 */
    private String employeeName;

    /** 部门名称 */
    private String deptName;

    /** 考勤日期 */
    private LocalDate attDate;

    /** 状态：NORMAL/ABSENT/LATE/LEAVE/OVERTIME */
    private String status;

    /** 备注 */
    private String remark;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
