package com.erp.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批次追溯节点 VO（正溯/反溯共用）。
 * <p>
 * 一个节点 = 一条与本批次相关的库存流水（inv_movement）：
 * 反溯（backward）取入库流水（qty &gt; 0，批次从哪来：入库单/采购单号）；
 * 正溯（forward）取出库流水（qty &lt; 0，批次到哪去：出库单/工单号）。
 * 按约定不 JOIN 采购/销售等业务实体，来源单据经 bizNo 由前端/上层自行关联。
 */
@Data
public class TraceNodeVO {

    /** inv_movement.id */
    private Long id;

    /** 业务类型（PUR_IN/OTH_OUT/MOVE/ADJUST/...） */
    private String bizType;

    /** 业务单据 ID */
    private Long bizId;

    /** 业务单号（正溯=去向单号，反溯=来源单号） */
    private String bizNo;

    /** 仓库 ID */
    private Long warehouseId;

    /** 仓库编码 */
    private String warehouseCode;

    /** 仓库名称 */
    private String warehouseName;

    /** 流水数量（反溯为正数，正溯为负数；调拨出/入两条流水同 bizNo） */
    private BigDecimal qty;

    /** 流水备注 */
    private String remark;

    /** 发生时间 */
    private LocalDateTime createTime;
}
