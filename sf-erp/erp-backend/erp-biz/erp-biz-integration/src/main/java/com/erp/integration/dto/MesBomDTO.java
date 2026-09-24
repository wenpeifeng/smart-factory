package com.erp.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * ERP BOM 下推 DTO（ERP -> MES 出站契约）。
 * <p>与 MES 侧 {@code com.mes.integration.erp.dto.ErpBomDTO} 逐字段对齐。
 */
@Data
public class MesBomDTO {
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
