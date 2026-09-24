package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售订单明细行响应 VO。
 * <p>
 * 物料编码/名称/规格由 Mapper 关联 base_material 查询带出；
 * remainingQty = qty - outQty，供出库选单时前端展示可出数量。
 */
@Data
public class SalOrderDetailVO {

    /** 行 ID */
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码（关联 base_material） */
    private String materialCode;

    /** 物料名称（关联 base_material） */
    private String materialName;

    /** 规格型号（关联 base_material） */
    private String materialSpec;

    /** 订购数量 */
    private BigDecimal qty;

    /** 含税单价 */
    private BigDecimal price;

    /** 金额 */
    private BigDecimal amount;

    /** 已出库数量 */
    private BigDecimal outQty;

    /** 剩余可出库数量 = qty - outQty（SQL 计算列） */
    private BigDecimal remainingQty;

    /** 行备注 */
    private String remark;
}
