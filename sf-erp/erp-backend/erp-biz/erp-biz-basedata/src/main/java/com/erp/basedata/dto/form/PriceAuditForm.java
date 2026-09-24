package com.erp.basedata.dto.form;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 特价审批意见表单（管理员通过/驳回时可选填）。
 */
@Data
public class PriceAuditForm {

    /** 审批意见 */
    @Size(max = 200, message = "审批意见长度不能超过200")
    private String remark;
}
