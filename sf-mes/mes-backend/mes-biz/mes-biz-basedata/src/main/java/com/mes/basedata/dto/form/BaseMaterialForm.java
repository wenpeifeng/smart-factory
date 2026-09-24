package com.mes.basedata.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料新增/修改表单。
 * <p>编码 code 可留空，留空时由后端按「WL+日期+3位流水」自动生成。
 */
@Data
public class BaseMaterialForm {

    /** 物料编码（留空自动生成） */
    @Size(max = 40, message = "物料编码长度不能超过40")
    private String code;

    /** 物料名称 */
    @NotBlank(message = "物料名称不能为空")
    @Size(max = 100, message = "物料名称长度不能超过100")
    private String name;

    /** 规格型号 */
    @Size(max = 200, message = "规格型号长度不能超过200")
    private String spec;

    /** 物料分类 ID */
    private Long categoryId;

    /** 计量单位 ID */
    private Long unitId;

    /** 条码 */
    @Size(max = 50, message = "条码长度不能超过50")
    private String barcode;

    /** 是否批次管理：1-是 0-否 */
    private Integer batchManaged;

    /** 是否序列号管理：1-是 0-否 */
    private Integer serialManaged;

    /** 安全库存，不能为负 */
    @DecimalMin(value = "0", message = "安全库存不能为负数")
    private BigDecimal safetyStock;

    /** 参考成本价，不能为负 */
    @DecimalMin(value = "0", message = "参考成本价不能为负数")
    private BigDecimal refCost;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
