package com.mes.integration.erp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MES 质检结果回传 DTO（MES -> ERP 出站契约）。
 * <p>检验单审核后回传 ERP（IQC 驱动采购收货判定 / FQC 驱动产成品入库判定）；
 * 按 inspectNo 幂等。
 */
@Data
public class ErpQualityResultDTO {
    /** MES 检验单号 */
    @NotBlank(message = "检验单号不能为空")
    private String inspectNo;
    /** 检验类型：IQC / IPQC / FQC */
    private String inspectType;
    /** 来源单据号（到货单/工单号） */
    private String sourceNo;
    /** 物料编码 */
    private String materialCode;
    /** 批次号 */
    private String batchNo;
    /** 抽检数量 */
    private BigDecimal sampleQty;
    /** 合格数量 */
    private BigDecimal qualifiedQty;
    /** 不合格数量 */
    private BigDecimal unqualifiedQty;
    /** 检验结论：1 合格 / 0 不合格 */
    private Integer conclusion;
    /** 检验员 */
    private String inspectorName;
    /** 检验日期 */
    private LocalDate inspectDate;
}
