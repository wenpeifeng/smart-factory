package com.erp.mrp.constant;

/**
 * 生产制造二期（车间作业/工艺路线/派工报工/能力计划）公共常量 ——
 * 与一期 {@link MrpConstants} 分文件维护，避免并行开发期互相污染。
 */
public final class MrpShopConstants {

    private MrpShopConstants() {
    }

    // ==================== 工作中心 / 工序 状态 ====================
    /** 工作中心/工序：启用 */
    public static final String ENABLED = "ENABLED";
    /** 工作中心/工序：停用 */
    public static final String DISABLED = "DISABLED";

    // ==================== 工艺路线状态（与 BOM 状态机一致） ====================
    /** 工艺路线：草稿 */
    public static final String ROUTING_DRAFT = "DRAFT";
    /** 工艺路线：已审核（有效） */
    public static final String ROUTING_ACTIVE = "ACTIVE";
    /** 工艺路线：停用 */
    public static final String ROUTING_DISABLED = "DISABLED";

    // ==================== 工单工序任务状态 ====================
    /** 工序任务：待派工 */
    public static final String STEP_PENDING_DISPATCH = "PENDING_DISPATCH";
    /** 工序任务：已派工 */
    public static final String STEP_DISPATCHED = "DISPATCHED";
    /** 工序任务：加工中（首次报工后） */
    public static final String STEP_PROCESSING = "PROCESSING";
    /** 工序任务：已完工（累计合格数达到计划数量） */
    public static final String STEP_FINISHED = "FINISHED";

    // ==================== 单号前缀 ====================
    /** 工作中心编码前缀 */
    public static final String PREFIX_WORK_CENTER = "WC";
    /** 工序编码前缀 */
    public static final String PREFIX_OPERATION = "OP";

    // ==================== 能力计划(CRP)参数 ====================
    /** CRP 期间默认天数（未传 from/to 时取 今天 ~ 今天+N） */
    public static final int CRP_DEFAULT_DAYS = 7;
    /** 每日默认班次开始时间（工单工序顺排的当日基准时刻） */
    public static final int DAY_SHIFT_START_HOUR = 8;
}
