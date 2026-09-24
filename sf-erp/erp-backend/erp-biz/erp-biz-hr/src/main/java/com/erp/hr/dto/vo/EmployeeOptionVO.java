package com.erp.hr.dto.vo;

import lombok.Data;

/**
 * 员工下拉选项（考勤筛选/汇总展示用）。
 */
@Data
public class EmployeeOptionVO {

    private Long id;

    private String empNo;

    private String name;

    /** 状态：ON_JOB/RESIGNED */
    private String status;
}
