package com.erp.sales.constant;

import java.math.BigDecimal;

/**
 * 销售前端链路（报价单 / 发货通知单）公共常量：状态值、单号前缀、业务阈值。
 * <p>
 * 与 {@link SalesConstants} 同层，因后者为既有文件（并行任务约定不改），
 * 本链路新增常量独立收口于此，状态取值与 V3111 建表脚本列 COMMENT 保持一致。
 */
public final class SalesLinkConstants {

    private SalesLinkConstants() {
    }

    // ==================== 报价单状态 ====================
    /** 报价单状态：草稿（可修改/可删除/可提交） */
    public static final int QUOT_DRAFT = 0;
    /** 报价单状态：已报价（可下推订单/可作废；过有效期后查询时动态显示为已过期） */
    public static final int QUOT_QUOTED = 10;
    /** 报价单状态：已转订单（终态，下推成功后回写） */
    public static final int QUOT_TO_ORDER = 20;
    /** 报价单状态：已过期（查询时动态判定：status=已报价 且 valid_until < 当天，不落库） */
    public static final int QUOT_EXPIRED = 30;
    /** 报价单状态：已作废（终态） */
    public static final int QUOT_VOID = 40;

    // ==================== 发货通知单状态 ====================
    /** 通知单状态：草稿（可修改/可删除） */
    public static final int NOTICE_DRAFT = 0;
    /** 通知单状态：已通知（可核销/可取消） */
    public static final int NOTICE_NOTIFIED = 10;
    /** 通知单状态：已出库（全部明细核销到位，终态） */
    public static final int NOTICE_OUT = 20;
    /** 通知单状态：已取消（终态，仅未发生出库时允许） */
    public static final int NOTICE_CANCELLED = 30;

    // ==================== 单号前缀（复用 SalesNoGenerator：前缀+yyyyMMdd+流水） ====================
    /** 报价单号前缀：BJ */
    public static final String NO_PREFIX_QUOTATION = "BJ";
    /** 发货通知单号前缀：FH */
    public static final String NO_PREFIX_NOTICE = "FH";

    // ==================== 毛利警戒线 ====================
    /** 行毛利率警戒线（%）：低于该值在毛利视图中标记警戒 */
    public static final BigDecimal MARGIN_WARN_THRESHOLD = new BigDecimal("20");
}
