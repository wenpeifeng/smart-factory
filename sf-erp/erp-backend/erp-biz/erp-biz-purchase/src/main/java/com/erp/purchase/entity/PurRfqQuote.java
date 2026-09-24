package com.erp.purchase.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 询比价报价行实体（pur_rfq_quote）。
 * <p>
 * 一行 = 某供应商对某询价明细的报价（单价+交期），
 * (rfq_item_id, supplier_id) 唯一；同一供应商重录报价 = 覆盖（先删后插）。
 * 定标校验：中标供应商必须存在对应报价行，保证 award_price 有据可依。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurRfqQuote extends BaseEntity {

    /** 询比价单 ID（pur_rfq.id） */
    private Long rfqId;

    /** 供应商 ID（base_supplier.id） */
    private Long supplierId;

    /** 询比价明细 ID（pur_rfq_item.id） */
    private Long rfqItemId;

    /** 物料 ID（base_material.id，冗余便于比价矩阵聚合） */
    private Long materialId;

    /** 报价单价（含税） */
    private BigDecimal price;

    /** 交期（天） */
    private Integer leadDays;

    /** 备注 */
    private String remark;
}
