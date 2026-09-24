package com.erp.purchase.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 询比价明细实体（pur_rfq_item）。
 * <p>
 * 定标信息（award_supplier_id/award_price/award_lead_days）在定标事务内写入；
 * pur_order_id 在下推事务内回写——一明细只允许下推一次，是「已下推禁再推」的判定依据。
 * 创建后明细不可修改（防止与已录报价错位），只能整单取消重开。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurRfqItem extends BaseEntity {

    /** 询比价单 ID（pur_rfq.id） */
    private Long rfqId;

    /** 来源请购明细 ID（pur_requisition_item.id，可空=独立询价行） */
    private Long requisitionItemId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 询价数量（>0，定标即采购量） */
    private BigDecimal qty;

    /** 定标供应商 ID（定标时写入） */
    private Long awardSupplierId;

    /** 中标单价（定标时取该供应商报价单价） */
    private BigDecimal awardPrice;

    /** 中标交期（天，定标时取该供应商报价交期） */
    private Integer awardLeadDays;

    /** 下推生成的采购订单 ID（pur_order.id，一明细仅下推一次） */
    private Long purOrderId;

    /** 备注 */
    private String remark;
}
