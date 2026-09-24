package com.erp.purchase.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 询比价新增表单（可关联请购单带入明细，也可独立发起）。
 * <p>
 * 业务规则：至少邀请 3 家供应商（比价才有意义）；
 * 关联请购时服务端逐行校验 询价数量 &lt;= 请购数量 - 已转采购数量。
 * 明细创建后不可修改（防与已录报价错位），只能整单取消重开；
 * 修改接口仅允许调整截止日期/备注/邀请供应商。
 */
@Data
public class PurRfqForm {

    /** 来源请购单 ID（可空=独立询价） */
    private Long requisitionId;

    /** 报价截止日期（必填） */
    @NotNull(message = "报价截止日期不能为空")
    private LocalDate deadline;

    /** 邀请供应商 ID 列表（至少 3 家，不可重复） */
    @NotEmpty(message = "邀请供应商不能为空")
    private List<Long> supplierIds;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 询价明细（至少一行） */
    @NotEmpty(message = "询价明细不能为空")
    @Valid
    private List<ItemForm> items;

    /**
     * 询价明细行。
     */
    @Data
    public static class ItemForm {

        /** 来源请购明细 ID（可空=手工添加行） */
        private Long requisitionItemId;

        /** 物料 ID（必填） */
        @NotNull(message = "物料不能为空")
        private Long materialId;

        /** 询价数量（必填，>0） */
        @NotNull(message = "数量不能为空")
        @DecimalMin(value = "0.0001", message = "数量必须大于 0")
        private BigDecimal qty;

        /** 行备注 */
        @Size(max = 200, message = "行备注最长 200 字")
        private String remark;
    }
}
