package com.mes.quality.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 检验单创建表单（选模板自动带出项目行）。
 */
@Data
public class QcInspectForm {
    /** 检验类型：IQC/IPQC/FQC */
    @NotBlank(message = "检验类型不能为空")
    private String inspectType;
    /** 来源类型：PURCHASE/WORKORDER/FINISH */
    private String sourceType;
    /** 来源单号 */
    private String sourceNo;
    /** 来源单据 ID */
    private Long sourceId;
    /** 检验模板 ID */
    @NotNull(message = "检验模板不能为空")
    private Long templateId;
    /** 工单 ID */
    private Long workorderId;
    /** 工单工序 ID */
    private Long woStepId;
    /** 物料 ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;
    /** 供应商 ID（IQC） */
    private Long supplierId;
    /** 批次号 */
    private String batchNo;
    /** 抽检数量 */
    @NotNull(message = "抽检数量不能为空")
    private BigDecimal sampleQty;
    /** 检验员姓名 */
    private String inspectorName;
    /** 备注 */
    private String remark;
}
