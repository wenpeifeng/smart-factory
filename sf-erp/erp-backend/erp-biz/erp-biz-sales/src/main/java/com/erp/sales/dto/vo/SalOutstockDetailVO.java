package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售出库单明细行响应 VO。
 * <p>
 * returnableQty = qty - returnedQty，供退货选单时前端展示可退数量。
 */
@Data
public class SalOutstockDetailVO {

    /** 行 ID */
    private Long id;

    /** 出库单 ID */
    private Long outId;

    /** 来源订单行 ID */
    private Long orderDetailId;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码（关联 base_material） */
    private String materialCode;

    /** 物料名称（关联 base_material） */
    private String materialName;

    /** 规格型号（关联 base_material） */
    private String materialSpec;

    /** 出库数量 */
    private BigDecimal qty;

    /** 含税单价 */
    private BigDecimal price;

    /** 金额 */
    private BigDecimal amount;

    /**
     * 出库实际成本（V2101）：审核时 CostService 移动加权计价回写；
     * NULL=历史出库行（销售统计回退 ref_cost 估算），草稿行为空。
     */
    private BigDecimal actualCost;

    /** 已被退货数量 */
    private BigDecimal returnedQty;

    /** 剩余可退数量 = qty - returnedQty（SQL 计算列） */
    private BigDecimal returnableQty;

    /** 行备注 */
    private String remark;
}
