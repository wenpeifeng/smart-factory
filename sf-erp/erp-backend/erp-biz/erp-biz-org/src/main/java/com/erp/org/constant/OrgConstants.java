package com.erp.org.constant;

/**
 * 组织管理模块常量。
 * <p>
 * 集中定义：通用启停状态、调拨价目加价类型、调拨单状态机、
 * 组织间调拨在 inv_movement 中的流水业务类型约定值。
 */
public final class OrgConstants {

    private OrgConstants() {
    }

    // ==================== 通用启停状态 ====================

    /** 停用 */
    public static final int STATUS_DISABLED = 0;
    /** 启用 */
    public static final int STATUS_ENABLED = 1;

    // ==================== 调拨价目加价类型 ====================

    /** 加价率%（markup_value=百分数，10 代表 10%） */
    public static final String MARKUP_RATE = "RATE";
    /** 固定加价额（markup_value=每单位加价金额） */
    public static final String MARKUP_FIXED = "FIXED";

    /** 加价率上限（百分数）：防误录 10000% 之类畸形价目 */
    public static final int MAX_RATE_PERCENT = 1000;

    // ==================== 调拨单状态机（单向：0→1 / 0→2） ====================

    /** 草稿（可修改/取消/审核） */
    public static final int TRANSFER_DRAFT = 0;
    /** 已审核（已过账库存，不可再变更） */
    public static final int TRANSFER_AUDITED = 1;
    /** 已取消（仅草稿可取消；已审核单不允许反向冲销，走红字新单） */
    public static final int TRANSFER_CANCELLED = 2;

    // ==================== 库存流水业务类型（inv_movement.biz_type 约定值） ====================

    /** 组织间调拨出库（调出仓，qty 为负） */
    public static final String BIZ_TYPE_ORG_OUT = "ORG_OUT";
    /** 组织间调拨入库（调入仓，qty 为正） */
    public static final String BIZ_TYPE_ORG_IN = "ORG_IN";

    /** 流水备注前缀：组织间调拨出库/入库，后接 IT 单号 */
    public static final String ORG_OUT_REMARK_PREFIX = "组织间调拨出库 ";
    public static final String ORG_IN_REMARK_PREFIX = "组织间调拨入库 ";

    /** 单号前缀：工厂 FC / 组织间调拨 IT */
    public static final String PREFIX_FACTORY = "FC";
    public static final String PREFIX_TRANSFER = "IT";
}
