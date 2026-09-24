package com.mes.integration.erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MES 完工报工回传 DTO（MES -> ERP 出站契约）。
 * <p>按 woNo+operationName 幂等：ERP 侧已存在则直接返回成功。
 */
@Data
public class ErpWorkReportDTO {
    /** MES 工单号 */
    @NotBlank(message = "工单号不能为空")
    private String woNo;
    /** 来源 ERP 生产订单号（回传时的 ERP 侧关联键） */
    private String erpOrderNo;
    /** 工序名称（空=工单完工汇总回传） */
    private String operationName;
    /** 合格数量 */
    @NotNull(message = "合格数量不能为空")
    @PositiveOrZero(message = "合格数量不能为负")
    private BigDecimal goodQty;
    /** 报废数量 */
    @NotNull(message = "报废数量不能为空")
    @PositiveOrZero(message = "报废数量不能为负")
    private BigDecimal scrapQty;
    /** 返修数量 */
    private BigDecimal reworkQty;
    /** 报工时间 */
    private LocalDateTime reportTime;
    /** 报工人 */
    private String workerName;
}
