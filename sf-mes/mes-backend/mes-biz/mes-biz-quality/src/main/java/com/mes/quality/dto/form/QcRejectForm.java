package com.mes.quality.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 不合格品处置单创建表单。
 */
@Data
public class QcRejectForm {
    /** 检验单 ID */
    @NotNull(message = "检验单不能为空")
    private Long inspectId;
    /** 处置数量 */
    @NotNull(message = "处置数量不能为空")
    @Positive(message = "处置数量必须大于0")
    private BigDecimal qty;
    /** 处置决定：1 返工 / 2 让步接收 / 3 报废 / 4 退货 */
    @NotNull(message = "处置决定不能为空")
    private Integer decision;
    /** 处置说明 */
    private String disposeRemark;
    /** 审批人 */
    private String approver;
}
