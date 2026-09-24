package com.mes.integration.erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ERP 生产订单下推 DTO（ERP -> MES 入站契约）。
 * <p>ERP 下推生产订单时按本结构 POST 到 {@code /integration/erp/orders}；
 * MES 按 orderNo 幂等：重复下推返回已有工单号，不重复建单。
 */
@Data
public class ErpProductionOrderDTO {
    /** ERP 生产订单号（幂等键） */
    @NotBlank(message = "ERP生产订单号不能为空")
    private String orderNo;
    /** 产品物料编码（base_material.code） */
    @NotBlank(message = "产品物料编码不能为空")
    private String materialCode;
    /** 产品物料名称（快照展示用） */
    private String materialName;
    /** 计划生产数量 */
    @NotNull(message = "生产数量不能为空")
    @Positive(message = "生产数量必须大于0")
    private BigDecimal qty;
    /** BOM 版本（空=物料当前生效版本） */
    private String bomVersion;
    /** 工艺路线编码（空=物料默认工艺路线） */
    private String routingCode;
    /** 默认发料仓库编码 */
    private String warehouseCode;
    /** SN 管理模式：BATCH 批次 / SN 单件 */
    private String snMode;
    /** 计划开工日期 */
    private LocalDate planStartDate;
    /** 计划完工日期 */
    private LocalDate planEndDate;
    /** 备注 */
    private String remark;
}
