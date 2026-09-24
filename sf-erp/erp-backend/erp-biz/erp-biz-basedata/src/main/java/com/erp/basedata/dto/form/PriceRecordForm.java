package com.erp.basedata.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购价格流水手工补录表单（历史导入/合同价补录）。
 * <p>
 * sourceType 固定落 CONTRACT（自动沉淀的三类 ORDER/INSTOCK/RFQ 由扫描器维护）。
 */
@Data
public class PriceRecordForm {

    /** 供应商 ID */
    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 单价（不含税） */
    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "单价必须大于0")
    private BigDecimal price;

    /** 价格日期 */
    @NotNull(message = "价格日期不能为空")
    private LocalDate priceDate;

    /** 来源单号（合同号/手工标识） */
    @Size(max = 30, message = "来源单号长度不能超过30")
    private String sourceNo;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
