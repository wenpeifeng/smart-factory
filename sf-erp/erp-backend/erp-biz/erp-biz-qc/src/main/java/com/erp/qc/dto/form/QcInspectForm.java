package com.erp.qc.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 检验单手工新增/草稿修改表单。
 * <p>
 * 明细行（items）可不传：服务端按 检验类型+物料分类 自动匹配启用模板带出检验项目；
 * 传了则整体覆盖（允许手工增删改检验项目）。
 */
@Data
public class QcInspectForm {

    /** 检验类型 */
    @NotBlank(message = "检验类型不能为空")
    @Pattern(regexp = "IQC|IPQC|FQC|OQC", message = "检验类型只允许 IQC/IPQC/FQC/OQC")
    private String inspectType;

    /** 来源单据类型（手工单可不传） */
    @Pattern(regexp = "pur_instock|mrp_work_order|sal_return",
            message = "来源单据类型只允许 pur_instock/mrp_work_order/sal_return")
    private String sourceType;

    /** 来源单据 ID（sourceType 非空时必填，服务层校验存在性） */
    private Long sourceId;

    /** 来源单号（快照，sourceType 非空时必填） */
    @Size(max = 40, message = "来源单号最长 40 字")
    private String sourceNo;

    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 供应商 ID（IQC 可选） */
    private Long supplierId;

    /** 抽检数量（>0） */
    @NotNull(message = "抽检数量不能为空")
    @DecimalMin(value = "0.0001", message = "抽检数量必须大于 0")
    private BigDecimal sampleQty;

    /** 备注 */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 检验项目明细（空=按模板自动带出；非空=整体覆盖） */
    @Valid
    private List<QcTemplateForm.TemplateItemForm> items;
}
