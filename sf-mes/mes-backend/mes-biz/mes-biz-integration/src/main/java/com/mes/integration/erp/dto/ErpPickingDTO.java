package com.mes.integration.erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES 领料回传 DTO（MES -> ERP 出站契约）。
 * <p>领料单审核后回传 ERP 做材料出库；按 pickNo 幂等。
 */
@Data
public class ErpPickingDTO {
    /** MES 领料单号 */
    @NotBlank(message = "领料单号不能为空")
    private String pickNo;
    /** MES 工单号 */
    private String woNo;
    /** 发料仓库编码（ERP 侧库存扣减仓库） */
    @NotBlank(message = "仓库编码不能为空")
    private String warehouseCode;
    /** 行项目 */
    @NotEmpty(message = "领料行项目不能为空")
    private List<Item> items;

    /** 领料行项目 */
    @Data
    public static class Item {
        /** 物料编码 */
        @NotBlank(message = "物料编码不能为空")
        private String materialCode;
        /** 批次号（批次不管理物料为空） */
        private String batchNo;
        /** 领料数量 */
        @NotNull(message = "领料数量不能为空")
        @Positive(message = "领料数量必须大于0")
        private BigDecimal qty;
    }
}
