package com.erp.purchase.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应商报价录入表单（按供应商整体提交，同供应商重录=覆盖）。
 * <p>
 * 仅询价中状态允许录入；供应商必须是本单被邀请方；
 * itemId 必须属于本单明细（防止串单）。
 */
@Data
public class RfqQuoteForm {

    /** 供应商 ID（必填，须为本单被邀请供应商） */
    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    /** 报价行（至少一行，需覆盖全部明细） */
    @NotEmpty(message = "报价明细不能为空")
    @Valid
    private List<QuoteLineForm> quotes;

    /**
     * 报价行。
     */
    @Data
    public static class QuoteLineForm {

        /** 询比价明细 ID（必填） */
        @NotNull(message = "询价明细不能为空")
        private Long itemId;

        /** 报价单价（必填，>=0） */
        @NotNull(message = "报价单价不能为空")
        @DecimalMin(value = "0", message = "报价单价不能为负数")
        private BigDecimal price;

        /** 交期天数（必填，>=0） */
        @NotNull(message = "交期不能为空")
        @Min(value = 0, message = "交期不能为负数")
        private Integer leadDays;
    }
}
