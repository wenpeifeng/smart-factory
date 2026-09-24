package com.erp.org.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 组织间调拨单新增/修改表单（修改仅草稿有效）。
 * <p>
 * 成本单价/内部结算单价由服务端按 cost_flow 最近加权成本 + 价目规则计算，
 * 前端只提交 物料×数量；同一单据允许同物料多行（不合并，保留录入口径）。
 */
@Data
public class TransferForm {

    /** 调出工厂 ID */
    @NotNull(message = "调出工厂不能为空")
    private Long fromFactoryId;

    /** 调出仓库 ID（须归属调出工厂） */
    @NotNull(message = "调出仓库不能为空")
    private Long fromWarehouseId;

    /** 调入工厂 ID（不能与调出工厂相同） */
    @NotNull(message = "调入工厂不能为空")
    private Long toFactoryId;

    /** 调入仓库 ID（须归属调入工厂） */
    @NotNull(message = "调入仓库不能为空")
    private Long toWarehouseId;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;

    /** 调拨明细行（至少一行） */
    @Valid
    @NotNull(message = "调拨明细不能为空")
    @Size(min = 1, max = 100, message = "调拨明细行数须在1~100之间")
    private List<Line> lines;

    /** 明细行 */
    @Data
    public static class Line {

        /** 物料 ID */
        @NotNull(message = "明细物料不能为空")
        private Long materialId;

        /** 调拨数量（>0） */
        @NotNull(message = "明细数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "调拨数量必须大于0")
        private BigDecimal qty;
    }
}
