package com.erp.sales.constant;

/**
 * 销售模块公共常量：单据状态值、单号前缀、跨模块业务类型。
 * <p>
 * 状态取值必须与 V301~V303 建表脚本中的列 COMMENT 保持一致，
 * 全模块统一通过本类引用，禁止散落魔法数字。
 */
public final class SalesConstants {

    private SalesConstants() {
    }

    // ==================== 销售订单状态 ====================
    /** 订单状态：草稿（可修改/可删除） */
    public static final int ORDER_DRAFT = 0;
    /** 订单状态：已审核（可出库/可关闭） */
    public static final int ORDER_AUDITED = 10;
    /** 订单状态：部分出库（可继续出库/可关闭） */
    public static final int ORDER_PART_OUT = 20;
    /** 订单状态：已完成（全部数量出库，终态） */
    public static final int ORDER_FINISHED = 30;
    /** 订单状态：已关闭（终态，剩余数量不再执行） */
    public static final int ORDER_CLOSED = 90;

    // ==================== 出库/退货单状态 ====================
    /** 出库单/退货单状态：草稿 */
    public static final int BIZ_DRAFT = 0;
    /** 出库单/退货单状态：已审核（执行单据，不可再改） */
    public static final int BIZ_AUDITED = 10;

    // ==================== 单号前缀（模块前缀 + yyyyMMdd + 4位流水） ====================
    /** 销售订单号前缀：SO202609050001 */
    public static final String NO_PREFIX_ORDER = "SO";
    /** 销售出库单号前缀：XS202609050001 */
    public static final String NO_PREFIX_OUTSTOCK = "XS";
    /** 销售退货单号前缀：RT202609050001 */
    public static final String NO_PREFIX_RETURN = "RT";

    // ==================== 库存流水业务类型（写入 inv_movement.biz_type，与 V401 约定一致） ====================
    /** 销售出库 */
    public static final String MOVEMENT_SALES_OUT = "SAL_OUT";
    /** 销售退货 */
    public static final String MOVEMENT_SALES_RETURN = "SAL_RET";

    // ==================== 应收状态（写入 fin_receivable.status，与 V501 约定一致） ====================
    /** 应收状态：未核销 */
    public static final String RECEIVABLE_UNSETTLED = "UNSETTLED";
}
