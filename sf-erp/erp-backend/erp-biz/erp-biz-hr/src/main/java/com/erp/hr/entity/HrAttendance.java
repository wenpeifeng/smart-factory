package com.erp.hr.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 考勤记录 —— 一员工一天一行（uk_emp_date），CSV 导入同键覆盖，
 * 适配"按月重复导入整月考勤"的场景。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_attendance")
public class HrAttendance extends BaseEntity implements Serializable {

    /** 员工ID（hr_employee.id） */
    private Long employeeId;

    /** 考勤日期 */
    private LocalDate attDate;

    /** 状态：NORMAL正常 ABSENT缺勤 LATE迟到 LEAVE请假 OVERTIME加班 */
    private String status;

    /** 备注 */
    private String remark;
}
