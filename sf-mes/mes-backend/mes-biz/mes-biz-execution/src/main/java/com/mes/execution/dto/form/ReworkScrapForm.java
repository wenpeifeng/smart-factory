package com.mes.execution.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 返工/报废单创建表单。
 */
@Data
public class ReworkScrapForm {
    /** 类型：REWORK 返工 / SCRAP 报废 */
    @NotBlank(message = "类型不能为空")
    private String type;
    /** 工单工序 ID */
    @NotNull(message = "工序任务不能为空")
    private Long woStepId;
    /** 产品 SN（SN 模式） */
    private String sn;
    /** 数量 */
    @NotNull(message = "数量不能为空")
    @Positive(message = "数量必须大于0")
    private BigDecimal qty;
    /** 原因（必填） */
    @NotBlank(message = "原因不能为空")
    private String reason;
    /** 关联质检不合格处置 ID（qc_reject.id） */
    private Long qcRejectId;
}
