package com.erp.hr.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工新增/修改表单。
 * <p>
 * 工号系统生成不接收；离职日期与状态走独立"离职办理"接口，防止随表单误改。
 */
@Data
public class EmployeeForm {

    /** 姓名 */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 30, message = "姓名最长 30 字")
    private String name;

    /** 性别：1男 2女 */
    @NotNull(message = "性别不能为空")
    private Integer gender;

    /** 手机号 */
    @Pattern(regexp = "^$|1[3-9]\\d{9}", message = "手机号格式不正确")
    private String phone;

    /** 身份证号（15 或 18 位） */
    @Pattern(regexp = "^$|(\\d{15}|\\d{17}[0-9Xx])", message = "身份证号格式不正确")
    private String idCard;

    /** 部门ID（sys_dept.id） */
    private Long deptId;

    /** 岗位 */
    @Size(max = 50, message = "岗位最长 50 字")
    private String position;

    /** 入职日期 */
    @NotNull(message = "入职日期不能为空")
    @PastOrPresent(message = "入职日期不能晚于今天")
    private LocalDate hireDate;

    /** 月薪基准 */
    @NotNull(message = "月薪基准不能为空")
    @PositiveOrZero(message = "月薪基准不能为负数")
    private BigDecimal baseSalary;

    /** 关联登录账号（sys_user.id，计件归集按工单制单人归属员工） */
    private Long userId;

    /** 备注 */
    @Size(max = 200, message = "备注最长 200 字")
    private String remark;
}
