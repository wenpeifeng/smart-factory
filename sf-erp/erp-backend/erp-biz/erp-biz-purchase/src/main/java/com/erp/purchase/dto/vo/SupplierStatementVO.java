package com.erp.purchase.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 供应商月度对账单 VO（GET /purchase/statements）。
 * 只读统计：净额 = 已审核入库金额 - 已审核退货金额。
 */
@Data
public class SupplierStatementVO {

    /** 供应商 ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 对账月份（yyyy-MM） */
    private String month;

    /** 采购入库金额（已审核入库单价税合计） */
    private BigDecimal instockAmount;

    /** 采购退货金额（已审核退货单价税合计） */
    private BigDecimal returnAmount;

    /** 净额 = 入库金额 - 退货金额 */
    private BigDecimal netAmount;

    /** 已审核入库单数 */
    private Long instockCount;

    /** 已审核退货单数 */
    private Long returnCount;
}
