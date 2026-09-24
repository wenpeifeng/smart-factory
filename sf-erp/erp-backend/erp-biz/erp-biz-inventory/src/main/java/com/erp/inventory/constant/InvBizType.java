package com.erp.inventory.constant;

/**
 * 库存业务类型枚举 —— inv_movement.biz_type 的合法取值集合。
 * <p>
 * 全系统约定：凡是引起库存变动的业务，必须以本枚举之一落库存流水。
 * 采购/销售/生产模块当前按约定表结构直接 INSERT inv_movement，
 * 后续统一升级为调用 {@link com.erp.inventory.service.StockService}（入参即本枚举）。
 */
public enum InvBizType {

    /** 采购入库（采购模块） */
    PUR_IN("PUR_IN", "采购入库"),

    /** 采购退货出库（采购模块） */
    PUR_RET("PUR_RET", "采购退货"),

    /** 销售出库（销售模块） */
    SAL_OUT("SAL_OUT", "销售出库"),

    /** 销售退货入库（销售模块） */
    SAL_RET("SAL_RET", "销售退货"),

    /** 其他入库（本模块，其他入库单审核） */
    OTH_IN("OTH_IN", "其他入库"),

    /** 其他出库（本模块，其他出库单审核） */
    OTH_OUT("OTH_OUT", "其他出库"),

    /** 盘点调整（本模块，盘点单完成时差异调平） */
    ADJUST("ADJUST", "盘点调整"),

    /** 仓库调拨（本模块，一出一入两条流水同单号） */
    MOVE("MOVE", "仓库调拨"),

    /** 生产入库（生产模块） */
    PROD_IN("PROD_IN", "生产入库"),

    /** 生产领料出库（生产模块） */
    PROD_USE("PROD_USE", "生产领料");

    /** 库存编码（入库表 biz_type 列的存储值） */
    private final String code;

    /** 中文说明（前端展示） */
    private final String label;

    InvBizType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
