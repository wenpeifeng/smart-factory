package com.erp.cost.constant;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 成本计价业务类型枚举 —— cost_flow.biz_type 的合法取值集合。
 * <p>
 * 与 inventory 模块 {@code InvBizType} 的编码一一对应（跨模块按约定表结构协作，
 * 不 import 对方代码）：凡是引起库存数量变动的业务，在存货计价流水侧使用同一编码，
 * 保证 {@code POST /cost/sync} 能直接以 biz_type 语义重放 inv_movement。
 */
public enum CostBizType {

    /** 采购入库（入库，计价金额取采购入库单明细金额） */
    PUR_IN("PUR_IN", "采购入库", true),

    /** 采购退货（出库，按移动加权价计价） */
    PUR_RET("PUR_RET", "采购退货", false),

    /** 销售出库（出库，月结转主营业务成本） */
    SAL_OUT("SAL_OUT", "销售出库", false),

    /** 销售退货（入库，按当前移动加权价入账） */
    SAL_RET("SAL_RET", "销售退货", true),

    /** 其他入库（入库，无单价的按当前移动加权价入账） */
    OTH_IN("OTH_IN", "其他入库", true),

    /** 其他出库（出库，月结转主营业务成本） */
    OTH_OUT("OTH_OUT", "其他出库", false),

    /** 盘点调整（盘盈入库/盘亏出库，按当前移动加权价计价） */
    ADJUST("ADJUST", "盘点调整", null),

    /** 仓库调拨（一出一入两行流水，按物料全仓加权时金额相互抵消，仅溯源） */
    MOVE("MOVE", "仓库调拨", null),

    /** 生产入库（入库，工单成本接通前按当前移动加权价入账） */
    PROD_IN("PROD_IN", "生产入库", true),

    /** 生产领料（出库，月结转生产成本） */
    PROD_USE("PROD_USE", "生产领料", false);

    /** 编码（cost_flow.biz_type 存储值） */
    private final String code;

    /** 中文说明（前端展示） */
    private final String label;

    /** 业务方向提示：true=入库方向 / false=出库方向 / null=双向（ADJUST/MOVE 按 qty 符号） */
    private final Boolean inDirection;

    /** code → 枚举索引（解析 inv_movement / 手工补录入参用） */
    private static final Map<String, CostBizType> CODE_MAP =
            Stream.of(values()).collect(Collectors.toMap(CostBizType::getCode, Function.identity()));

    CostBizType(String code, String label, Boolean inDirection) {
        this.code = code;
        this.label = label;
        this.inDirection = inDirection;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 是否入库方向业务（true=入库 / false=出库 / null=双向（ADJUST/MOVE 按 qty 符号判定）） */
    public Boolean isInDirection() {
        return inDirection;
    }

    /** 按编码解析，非法编码返回 null（调用方自行抛业务异常） */
    public static CostBizType ofCode(String code) {
        return code == null ? null : CODE_MAP.get(code.trim().toUpperCase());
    }
}
