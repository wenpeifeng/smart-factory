package com.erp.invoice.constant;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 发票管理模块公共常量。
 * <p>
 * 销项 / 进项发票共用同一套状态值（语义按表注释区分）：
 * 0 待开票/待认证 → 10 已开票/已认证 → 20 已红冲（终态，由红字负数票冲减）。
 */
public final class InvoiceConstants {

    private InvoiceConstants() {
    }

    /** 状态：待开票（销项）/ 待认证（进项） */
    public static final int STATUS_PENDING = 0;
    /** 状态：已开票（销项）/ 已认证（进项） */
    public static final int STATUS_ISSUED_OR_AUTHED = 10;
    /** 状态：已红冲（终态） */
    public static final int STATUS_REVERSED = 20;

    /** 红字标志：蓝字 */
    public static final int BLUE = 0;
    /** 红字标志：红字（负数发票） */
    public static final int RED = 1;

    /** 业务单据类型：销售出库单 */
    public static final String BIZ_SAL_OUTSTOCK = "SAL_OUTSTOCK";
    /** 业务单据类型：采购入库单 */
    public static final String BIZ_PUR_INSTOCK = "PUR_INSTOCK";

    /** 默认税率（%） */
    public static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("13.00");

    /** 允许的增值税税率集合（百分数：13 / 9 / 6） */
    public static final Set<BigDecimal> ALLOWED_TAX_RATES =
            Set.of(new BigDecimal("13"), new BigDecimal("9"), new BigDecimal("6"));

    /** 销项出库单已审核状态（sal_outstock.status，跨模块约定值） */
    public static final int OUTSTOCK_AUDITED = 10;
    /** 采购入库单已审核状态（pur_instock.status，跨模块约定值） */
    public static final int INSTOCK_AUDITED = 10;
}
