package com.erp.purchase.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购入库单 VO（列表行 + 详情）。
 */
@Data
public class PurInstockVO {

    private Long id;

    /** 入库单号 */
    private String instockNo;

    /** 来源采购订单 ID */
    private Long orderId;

    /** 来源采购订单号 */
    private String orderNo;

    /** 供应商 ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 入库仓库 ID */
    private Long warehouseId;

    /** 仓库名称 */
    private String warehouseName;

    /** 入库日期 */
    private LocalDate instockDate;

    /** 价税合计 */
    private BigDecimal totalAmount;

    /** 状态：0草稿 10已审核 */
    private Integer status;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 明细（详情接口返回） */
    private List<PurInstockDetailVO> details;

    /**
     * 采购入库明细 VO。
     */
    @Data
    public static class PurInstockDetailVO {

        private Long id;

        /** 来源订单明细 ID */
        private Long orderDetailId;

        /** 物料 ID */
        private Long materialId;

        /** 入库批次号（空串=非批次物料汇总行；V2101 起 StockService 批次入库依据） */
        private String batchNo;

        /** 物料编码 */
        private String materialCode;

        /** 物料名称 */
        private String materialName;

        /** 规格 */
        private String spec;

        /** 计量单位 */
        private String unit;

        /** 实收数量 */
        private BigDecimal qty;

        /** 含税单价 */
        private BigDecimal price;

        /** 税率（百分数） */
        private BigDecimal taxRate;

        /** 含税金额 */
        private BigDecimal amount;

        /** 本明细累计已退数量 */
        private BigDecimal returnedQty;

        /** 行备注 */
        private String remark;
    }
}
