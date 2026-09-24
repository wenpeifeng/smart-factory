package com.erp.hr.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工档案 VO —— 身份证号脱敏返回（保留前 6 后 4 位）。
 */
@Data
public class EmployeeVO {

    private Long id;

    /** 工号 */
    private String empNo;

    /** 姓名 */
    private String name;

    /** 性别：1男 2女 */
    private Integer gender;

    /** 手机号 */
    private String phone;

    /** 身份证号（脱敏：前6后4，中间 * ） */
    private String idCard;

    /** 部门ID */
    private Long deptId;

    /** 部门名称（sys_dept 回显） */
    private String deptName;

    /** 岗位 */
    private String position;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 离职日期 */
    private LocalDate resignDate;

    /** 状态：ON_JOB在职 RESIGNED离职 */
    private String status;

    /** 月薪基准 */
    private BigDecimal baseSalary;

    /** 关联登录账号 */
    private Long userId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 身份证号脱敏：保留前 6 位与后 4 位，中间以 * 补齐到原长度；空值原样返回。
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 11) {
            return idCard;
        }
        int len = idCard.length();
        return idCard.substring(0, 6) + "*".repeat(len - 10) + idCard.substring(len - 4);
    }
}
