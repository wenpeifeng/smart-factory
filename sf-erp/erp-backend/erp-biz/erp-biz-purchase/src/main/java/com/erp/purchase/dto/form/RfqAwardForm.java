package com.erp.purchase.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 询比价定标表单（支持分项定标：两行物料可选不同供应商）。
 * <p>
 * perItem 必须覆盖本单全部明细各一次（一明细一中标供应商）；
 * 中标供应商必须已对该明细录过报价（中标单价/交期取自报价行）。
 */
@Data
public class RfqAwardForm {

    /** 分项定标结果（每明细一条） */
    @NotEmpty(message = "定标结果不能为空")
    @Valid
    private List<AwardItemForm> perItem;

    /**
     * 分项定标行。
     */
    @Data
    public static class AwardItemForm {

        /** 询比价明细 ID（必填） */
        @NotNull(message = "询价明细不能为空")
        private Long itemId;

        /** 中标供应商 ID（必填） */
        @NotNull(message = "中标供应商不能为空")
        private Long supplierId;
    }
}
