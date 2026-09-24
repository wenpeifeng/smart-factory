package com.erp.purchase.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 询比价供应商实体（pur_rfq_supplier）。
 * <p>
 * 一次询价邀请多家（业务规则至少 3 家），(rfq_id, supplier_id) 唯一。
 * quote_amount 在录报价时按 明细数量×报价单价 汇总回写，便于比价总览。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurRfqSupplier extends BaseEntity {

    /** 状态：已邀请（未报价） */
    public static final int STATUS_INVITED = 0;
    /** 状态：已报价 */
    public static final int STATUS_QUOTED = 10;
    /** 状态：中标（定标时对中标分项的供应商标记） */
    public static final int STATUS_WON = 20;

    /** 询比价单 ID（pur_rfq.id） */
    private Long rfqId;

    /** 供应商 ID（base_supplier.id） */
    private Long supplierId;

    /** 报价金额合计（明细数量×报价单价之和） */
    private BigDecimal quoteAmount;

    /** 状态：0已邀请 10已报价 20中标 */
    private Integer status;

    /** 备注 */
    private String remark;
}
