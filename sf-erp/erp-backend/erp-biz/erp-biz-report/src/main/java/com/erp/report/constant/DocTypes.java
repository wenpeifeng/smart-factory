package com.erp.report.constant;

import java.util.Map;
import java.util.Set;

/**
 * 单据联查 / 通用红冲的单据类型编码（T7 约定）。
 * <p>
 * 编码与表名一一对应，作为 doc-chain 与 reverse 接口的 bizType/docType 入参。
 * 红冲支持集见 {@link #REVERSIBLE}；链路邻接关系见 DocChainMapper.xml 注释。
 */
public final class DocTypes {

    private DocTypes() {
    }

    // ==================== 单据类型（bizType / docType） ====================

    public static final String SAL_QUOTATION = "SAL_QUOTATION";
    public static final String SAL_ORDER = "SAL_ORDER";
    public static final String SAL_OUTSTOCK = "SAL_OUTSTOCK";
    public static final String SAL_RETURN = "SAL_RETURN";
    public static final String PUR_ORDER = "PUR_ORDER";
    public static final String PUR_INSTOCK = "PUR_INSTOCK";
    public static final String PUR_RETURN = "PUR_RETURN";
    public static final String SALES_INVOICE = "SALES_INVOICE";
    public static final String PURCHASE_INVOICE = "PURCHASE_INVOICE";
    public static final String RECEIVABLE = "RECEIVABLE";
    public static final String PAYABLE = "PAYABLE";
    public static final String RECEIPT = "RECEIPT";
    public static final String PAYMENT = "PAYMENT";
    public static final String VOUCHER = "VOUCHER";

    /** 支持红冲的单据（均已审核过账、可负数反向） */
    public static final Set<String> REVERSIBLE = Set.of(
            PUR_INSTOCK, SAL_OUTSTOCK, PUR_RETURN, SAL_RETURN, RECEIPT, PAYMENT);

    /** docType -> 中文名（联查节点展示） */
    public static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry(SAL_QUOTATION, "销售报价单"),
            Map.entry(SAL_ORDER, "销售订单"),
            Map.entry(SAL_OUTSTOCK, "销售出库单"),
            Map.entry(SAL_RETURN, "销售退货单"),
            Map.entry(PUR_ORDER, "采购订单"),
            Map.entry(PUR_INSTOCK, "采购入库单"),
            Map.entry(PUR_RETURN, "采购退货单"),
            Map.entry(SALES_INVOICE, "销售发票"),
            Map.entry(PURCHASE_INVOICE, "采购发票"),
            Map.entry(RECEIVABLE, "应收单"),
            Map.entry(PAYABLE, "应付单"),
            Map.entry(RECEIPT, "收款单"),
            Map.entry(PAYMENT, "付款单"),
            Map.entry(VOUCHER, "记账凭证"));

    /** docType -> 物理表名（红冲状态回写白名单，防 ${} 注入） */
    public static final Map<String, String> STATUS_TABLES = Map.of(
            PUR_INSTOCK, "pur_instock",
            SAL_OUTSTOCK, "sal_outstock",
            PUR_RETURN, "pur_return",
            SAL_RETURN, "sal_return");

    /** 库存流水 biz_type（inv_movement）-> 单据类型，红冲按流水腿精确反向 */
    public static final Map<String, String> MOVEMENT_DOC = Map.of(
            "PUR_IN", PUR_INSTOCK,
            "PUR_RET", PUR_RETURN,
            "SAL_OUT", SAL_OUTSTOCK,
            "SAL_RET", SAL_RETURN);

    public static String label(String docType) {
        return LABELS.getOrDefault(docType, docType);
    }
}
