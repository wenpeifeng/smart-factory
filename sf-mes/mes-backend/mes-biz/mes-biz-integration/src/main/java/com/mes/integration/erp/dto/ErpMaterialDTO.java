package com.mes.integration.erp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * ERP 物料主数据下推 DTO（ERP -> MES 入站契约）。
 * <p>按 code 幂等 upsert 到 base_material；ERP 侧全量+增量均可。
 */
@Data
public class ErpMaterialDTO {
    /** 物料编码 */
    @NotBlank(message = "物料编码不能为空")
    private String code;
    /** 物料名称 */
    @NotBlank(message = "物料名称不能为空")
    private String name;
    /** 规格型号 */
    private String spec;
    /** 单位 */
    private String unit;
    /** 物料类型（RAW 原料 / SEMI 半成品 / FG 成品） */
    private String materialType;
    /** 物料类别编码 */
    private String categoryCode;
    /** 是否批次管理：1 是 / 0 否 */
    private Integer batchManaged;
    /** 是否关键件（关键件SN追溯）：1 是 / 0 否 */
    private Integer keyComponent;
    /** 单重/净重（kg，工艺用） */
    private BigDecimal weight;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    /** 备注 */
    private String remark;
}
