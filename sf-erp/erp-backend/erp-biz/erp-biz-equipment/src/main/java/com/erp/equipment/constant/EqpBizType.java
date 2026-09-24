package com.erp.equipment.constant;

/**
 * 设备模块库存业务类型 —— 本模块写 inv_movement 时 biz_type 的合法取值集合。
 * <p>
 * 背景约定（见 docs/modules/inventory.md）：库存模块的
 * {@code com.erp.inventory.constant.InvBizType} 枚举目前只收录了
 * PUR_IN/PUR_RET/SAL_OUT/SAL_RET/OTH_IN/OTH_OUT/ADJUST/MOVE/PROD_IN/PROD_USE 十种，
 * 尚未包含设备备件出入库类型。按「二期新模块不自改他人模块」的并行开发约定，
 * 本模块自建以下常量直接落库，待主线程统一合入 InvBizType 枚举：
 * <ul>
 *   <li>{@link #EQP_OUT}：维修工单完工领用备件出库（当前使用）；</li>
 *   <li>{@link #EQP_IN}：备件退回入库（预留，当前无业务入口）。</li>
 * </ul>
 * 落库遵循库存模块三约定：qty 正入负出、库存与流水同事务、扣减带条件 UPDATE 防负库存。
 */
public final class EqpBizType {

    /** 维修工单完工领用备件出库（设备模块） */
    public static final String EQP_OUT = "EQP_OUT";

    /** 备件退回入库（设备模块，预留） */
    public static final String EQP_IN = "EQP_IN";

    private EqpBizType() {
    }
}
