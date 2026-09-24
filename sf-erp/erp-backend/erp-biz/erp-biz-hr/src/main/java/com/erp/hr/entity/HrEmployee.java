package com.erp.hr.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工档案 —— 工号系统生成（EMP+yyyyMMdd+4位流水），身份证号出参脱敏。
 * <p>
 * 状态机：ON_JOB在职 → RESIGNED离职（离职办理填写 resign_date，单向不可逆）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_employee")
public class HrEmployee extends BaseEntity implements Serializable {

    /** 工号（EMP+yyyyMMdd+4位流水） */
    private String empNo;

    /** 姓名 */
    private String name;

    /** 性别：1男 2女 */
    private Integer gender;

    /** 手机号 */
    private String phone;

    /** 身份证号（VO 返回时脱敏） */
    private String idCard;

    /** 部门ID（sys_dept.id） */
    private Long deptId;

    /** 岗位 */
    private String position;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 离职日期（离职办理时填写） */
    private LocalDate resignDate;

    /** 状态：ON_JOB在职 RESIGNED离职 */
    private String status;

    /** 月薪基准（缺勤扣款基数：月薪/30×缺勤天数） */
    private BigDecimal baseSalary;

    /** 关联登录账号（sys_user.id，计件归集按工单制单人归属员工） */
    private Long userId;

    /** 备注 */
    private String remark;
}
