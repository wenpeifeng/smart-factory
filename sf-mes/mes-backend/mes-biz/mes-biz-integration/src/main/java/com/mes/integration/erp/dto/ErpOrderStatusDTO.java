package com.mes.integration.erp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MES 工单进度镜像 DTO（MES -> ERP，POST /integration/mes/order-status）。
 * <p>仅更新 ERP 侧 mes_status 镜像列，不驱动 ERP 状态机。
 */
@Data
public class ErpOrderStatusDTO {
    /** MES 工单号 */
    @NotBlank(message = "工单号不能为空")
    private String woNo;
    /** ERP 生产订单号 */
    private String erpOrderNo;
    /** MES 状态 */
    @NotBlank(message = "状态不能为空")
    private String mesStatus;
    /** 累计合格数 */
    private BigDecimal goodQty;
    /** 累计报废数 */
    private BigDecimal scrapQty;
    /** 事件时间 */
    private LocalDateTime eventTime;
}
