package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产订单 VO（含物料编码/名称回显）。
 */
@Data
public class WorkOrderVO implements Serializable {

    /** 订单ID */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 成品物料ID */
    private Long materialId;

    /** 成品物料编码 */
    private String materialCode;

    /** 成品物料名称 */
    private String materialName;

    /** 计量单位 */
    private String unit;

    /** 计划生产数量 */
    private BigDecimal qty;

    /** BOMID */
    private Long bomId;

    /** BOM版本 */
    private String bomVersion;

    /** 默认入库仓库 */
    private Long warehouseId;

    /** 计划开始日期 */
    private LocalDate planStartDate;

    /** 计划结束日期 */
    private LocalDate planEndDate;

    /** 领料方式：DIRECT 直接领料 / BACKFLUSH 倒冲（V621） */
    private String pickType;

    /** 倒冲扣料仓库（空=沿用 warehouseId） */
    private Long backflushWarehouseId;

    /** 状态：DRAFT/RELEASED/PRODUCING/FINISHED/CLOSED */
    private String status;

    /** 累计完工良品数量 */
    private BigDecimal finishQty;

    /** 累计不良品数量 */
    private BigDecimal badQty;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据ID */
    private Long sourceId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
