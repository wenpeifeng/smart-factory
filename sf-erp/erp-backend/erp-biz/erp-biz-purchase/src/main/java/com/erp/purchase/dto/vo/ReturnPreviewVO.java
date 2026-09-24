package com.erp.purchase.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购退货单"选单"预填充 VO（GET /purchase/returns/from-instock/{instockId}）。
 * 前端选择已审核入库单后，按本结构预填表单；建议退货数量=入库数量-累计已退。
 */
@Data
public class ReturnPreviewVO {

    /** 来源入库单 ID */
    private Long instockId;

    /** 入库单号 */
    private String instockNo;

    /** 供应商 ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 退货仓库 ID（取自入库单） */
    private Long warehouseId;

    /** 仓库名称 */
    private String warehouseName;

    /** 入库日期 */
    private LocalDate instockDate;

    /** 可退货明细（已过滤建议数量<=0 的行） */
    private List<ReturnPreviewDetailVO> details;

    /**
     * 选单明细行。
     */
    @Data
    public static class ReturnPreviewDetailVO {

        /** 入库明细 ID */
        private Long instockDetailId;

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

        /** 含税单价（入库） */
        private BigDecimal price;

        /** 税率（入库） */
        private BigDecimal taxRate;

        /** 入库数量 */
        private BigDecimal instockQty;

        /** 累计已退数量 */
        private BigDecimal returnedQty;

        /** 建议退货数量 = 入库数量 - 累计已退 */
        private BigDecimal suggestQty;
    }
}
