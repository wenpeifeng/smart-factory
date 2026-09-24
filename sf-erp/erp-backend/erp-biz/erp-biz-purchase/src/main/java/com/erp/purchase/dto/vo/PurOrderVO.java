package com.erp.purchase.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单 VO（列表行 + 详情）。
 * 列表查询时 details 为 null，详情查询时填充。
 */
@Data
public class PurOrderVO {

    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 供应商 ID */
    private Long supplierId;

    /** 供应商名称（JOIN base_supplier） */
    private String supplierName;

    /** 默认入库仓库 ID */
    private Long warehouseId;

    /** 仓库名称（JOIN base_warehouse） */
    private String warehouseName;

    /** 订单日期 */
    private LocalDate orderDate;

    /** 价税合计 */
    private BigDecimal totalAmount;

    /** 状态：0草稿 10已审核 20部分入库 30已完成 90已关闭 */
    private Integer status;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 明细（详情接口返回） */
    private List<PurOrderDetailVO> details;

    /**
     * 采购订单明细 VO（含物料编码/名称/规格/单位投影）。
     */
    @Data
    public static class PurOrderDetailVO {

        private Long id;

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

        /** 采购数量 */
        private BigDecimal qty;

        /** 含税单价 */
        private BigDecimal price;

        /** 税率（百分数） */
        private BigDecimal taxRate;

        /** 含税金额 */
        private BigDecimal amount;

        /** 要求交货日期 */
        private LocalDate deliveryDate;

        /** 累计已入库数量 */
        private BigDecimal receivedQty;

        /** 行备注 */
        private String remark;
    }
}
