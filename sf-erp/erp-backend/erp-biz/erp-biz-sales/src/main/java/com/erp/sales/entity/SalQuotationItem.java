package com.erp.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售报价单明细行（表 sal_quotation_item，V3111）。
 * <p>
 * cost_price 为录入时 base_material.ref_cost 的只读快照（防物料后续调价导致
 * 历史报价毛利失真），不接受前端回传；amount 已含整单折扣。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_quotation_item")
public class SalQuotationItem extends BaseEntity {

    /** 报价单 ID（sal_quotation.id） */
    private Long quotationId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 报价数量 */
    private BigDecimal qty;

    /** 报价单价 */
    private BigDecimal price;

    /** 成本参考（base_material.ref_cost 录入时快照，只读带回） */
    private BigDecimal costPrice;

    /** 行金额 = qty × price × (1 − 整单折扣率/100)，四舍五入 2 位 */
    private BigDecimal amount;

    /** 行备注 */
    private String remark;
}
