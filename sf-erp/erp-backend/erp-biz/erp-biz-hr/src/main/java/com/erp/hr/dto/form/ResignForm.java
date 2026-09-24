package com.erp.hr.dto.form;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工离职办理表单 —— 填写离职日期后员工状态置为 RESIGNED（单向不可逆）。
 */
@Data
public class ResignForm {

    /** 离职日期 */
    @NotNull(message = "离职日期不能为空")
    private LocalDate resignDate;
}
