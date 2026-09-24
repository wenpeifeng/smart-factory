package com.mes.integration.erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * ERP BOM 下推 DTO（ERP -> MES 入站契约）。
 * <p>按 materialCode+version 幂等 upsert 到 base_bom / base_bom_item。
 */
@Data
public class ErpBomDTO {
    /** 产品物料编码 */
    @NotBlank(message = "产品物料编码不能为空")
    private String materialCode;
    /** BOM 版本 */
    @NotBlank(message = "BOM版本不能为空")
    private String version;
    /** 行项目 */
    @NotEmpty(message = "BOM行项目不能为空")
    private List<Item> items;

    /** BOM 行项目 */
    @Data
    public static class Item {
        /** 子件物料编码 */
        @NotBlank(message = "子件物料编码不能为空")
        private String materialCode;
        /** 单位用量 */
        @NotNull(message = "单位用量不能为空")
        @Positive(message = "单位用量必须大于0")
        private BigDecimal qty;
        /** 损耗率（%） */
        private BigDecimal lossRate;
        /** 是否关键件：1 是 / 0 否 */
        private Integer keyComponent;
    }
}
