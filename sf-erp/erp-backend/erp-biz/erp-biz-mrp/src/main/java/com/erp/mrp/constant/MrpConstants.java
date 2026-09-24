package com.erp.mrp.constant;

/**
 * 生产制造模块公共常量 —— 状态机取值与固定业务参数集中定义，
 * 避免字符串魔法值散落在 Service/Controller 之间造成不一致。
 */
public final class MrpConstants {

    private MrpConstants() {
    }

    // ==================== BOM 状态 ====================
    /** BOM：草稿 */
    public static final String BOM_DRAFT = "DRAFT";
    /** BOM：已审核（有效） */
    public static final String BOM_ACTIVE = "ACTIVE";
    /** BOM：停用 */
    public static final String BOM_DISABLED = "DISABLED";

    // ==================== MPS 状态 ====================
    /** MPS：待运算 */
    public static final String MPS_PENDING = "PENDING";
    /** MPS：已运算 */
    public static final String MPS_CALCULATED = "CALCULATED";

    // ==================== 建议类型 / 状态 ====================
    /** 建议：采购 */
    public static final String PLAN_PURCHASE = "PURCHASE";
    /** 建议：生产 */
    public static final String PLAN_PRODUCE = "PRODUCE";
    /** 建议：待处理 */
    public static final String SUGGESTION_PENDING = "PENDING";
    /** 建议：已转单 */
    public static final String SUGGESTION_CONVERTED = "CONVERTED";
    /** 建议转单引用类型：生产订单 */
    public static final String REF_WORK_ORDER = "WORK_ORDER";
    /** 建议转单引用类型：采购订单（mrp → purchase 接线） */
    public static final String REF_PUR_ORDER = "PUR_ORDER";

    // ==================== 生产订单状态 ====================
    /** 生产订单：草稿 */
    public static final String WO_DRAFT = "DRAFT";
    /** 生产订单：已下达 */
    public static final String WO_RELEASED = "RELEASED";
    /** 生产订单：生产中 */
    public static final String WO_PRODUCING = "PRODUCING";
    /** 生产订单：已完工 */
    public static final String WO_FINISHED = "FINISHED";
    /** 生产订单：已关闭 */
    public static final String WO_CLOSED = "CLOSED";
    /** 生产订单来源：手工创建 */
    public static final String WO_SOURCE_MANUAL = "MANUAL";
    /** 生产订单来源：MRP 建议转单 */
    public static final String WO_SOURCE_SUGGESTION = "MRP_SUGGESTION";

    // ==================== 领料单状态 ====================
    /** 领料单：草稿 */
    public static final String ISSUE_DRAFT = "DRAFT";
    /** 领料单：已审核 */
    public static final String ISSUE_AUDITED = "AUDITED";

    // ==================== 领料方式 / 领料单据类型（V621 倒冲） ====================
    /** 领料方式：直接领料（手工领料单审核出库） */
    public static final String PICK_DIRECT = "DIRECT";
    /** 领料方式：倒冲（完工报工按 BOM×完工数量自动出库） */
    public static final String PICK_BACKFLUSH = "BACKFLUSH";
    /** 领料单类型：手工领料 */
    public static final String ISSUE_TYPE_NORMAL = "NORMAL";
    /** 领料单类型：完工倒冲自动生成（直接落 AUDITED） */
    public static final String ISSUE_TYPE_BACKFLUSH = "BACKFLUSH";

    // ==================== 委外单状态 ====================
    /** 委外单：草稿 */
    public static final String OS_DRAFT = "DRAFT";
    /** 委外单：已发料 */
    public static final String OS_ISSUED = "ISSUED";
    /** 委外单：已完工入库 */
    public static final String OS_RECEIVED = "RECEIVED";

    // ==================== 库存流水业务类型（inv_movement.biz_type 集成约定） ====================
    /** 生产领料出库 */
    public static final String MOV_PRODUCTION_USE = "PROD_USE";
    /** 生产完工入库 */
    public static final String MOV_PRODUCTION_IN = "PROD_IN";
    /** 委外发料出库 */
    public static final String MOV_OUTSOURCE_USE = "OUT_USE";
    /** 委外完工入库 */
    public static final String MOV_OUTSOURCE_IN = "OUT_IN";
    /** 委外应付业务类型（fin_payable 集成约定） */
    public static final String PAYABLE_OUTSOURCE = "OUTSOURCE";

    // ==================== MRP 运算固定参数 ====================
    /** 固定提前期（天）——base_material 无提前期字段时使用 */
    public static final int LEAD_TIME_DAYS = 7;
    /** BOM 展开深度上限（防过深递归/防环） */
    public static final int MAX_BOM_LEVEL = 15;

    // ==================== 单号前缀 ====================
    /** 生产订单前缀 */
    public static final String PREFIX_WORK_ORDER = "MO";
    /** 领料单前缀 */
    public static final String PREFIX_ISSUE = "MI";
    /** 委外单前缀 */
    public static final String PREFIX_OUTSOURCE = "OS";
    /** MRP 运算批次前缀 */
    public static final String PREFIX_CALC_BATCH = "MR";
}
