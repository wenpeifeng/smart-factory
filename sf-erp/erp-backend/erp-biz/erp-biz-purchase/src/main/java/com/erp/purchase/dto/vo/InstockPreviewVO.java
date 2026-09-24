package com.erp.purchase.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购入库单"选单"预填充 VO（GET /purchase/instock/from-order/{orderId}）。
 * 前端选择已审核采购订单后，按本结构预填表单；建议实收数量=订单数量-累计已入库。
 */
@Data
public class InstockPreviewVO {

    /** 来源采购订单 ID */
    private Long orderId;

    /** 采购订单号 */
    private String orderNo;

    /** 供应商 ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 入库仓库 ID（订单默认仓库） */
    private Long warehouseId;

    /** 仓库名称 */
    private String warehouseName;

    /** 订单日期（供默认入库日期参考） */
    private LocalDate orderDate;

    /** 可入库明细（已过滤建议数量<=0 的行） */
    private List<InstockPreviewDetailVO> details;

    /**
     * 选单明细行。
     */
    @Data
    public static class InstockPreviewDetailVO {

        /** 订单明细 ID */
        private Long orderDetailId;

        /** 物料 ID */
        private Long materialId;

        /** 物料编码 */
        private String materialCode;

        /** 物料名称 */
        private String materialName;

        /** 规格 */
        private String spec;

        /** 计量单位 */
        private String unit;

        /** 含税单价（订单） */
        private BigDecimal price;

        /** 税率（订单） */
        private BigDecimal taxRate;

        /** 订单数量 */
        private BigDecimal orderQty;

        /** 累计已入库数量 */
        private BigDecimal receivedQty;

        /** 建议实收数量 = 订单数量 - 累计已入库 */
        private BigDecimal suggestQty;

        /** 要求交货日期 */
        private LocalDate deliveryDate;
    }
}
