package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售报价单明细行 VO（JOIN base_material 带出物料信息与成本快照）。
 */
@Data
public class SalQuotationItemVO {

    /** 明细行 ID */
    private Long id;

    /** 报价单 ID */
    private Long quotationId;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码（JOIN base_material） */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 规格型号 */
    private String materialSpec;

    /** 报价数量 */
    private BigDecimal qty;

    /** 报价单价 */
    private BigDecimal price;

    /** 成本参考（base_material.ref_cost 录入时快照，只读带回） */
    private BigDecimal costPrice;

    /** 行金额（已含整单折扣） */
    private BigDecimal amount;

    /** 行备注 */
    private String remark;
}
