package com.erp.purchase.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购入库单明细实体（pur_instock_detail）。
 * <p>
 * 每行关联订单明细（order_detail_id），实收数量可小于订单数量（一次分批入库），
 * 禁止超收（校验在服务层）。returned_qty 记录本明细累计被退货数量。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurInstockDetail extends BaseEntity {

    /** 入库单 ID（pur_instock.id） */
    private Long instockId;

    /** 来源订单明细 ID（pur_order_detail.id，用于回写订单累计入库数量） */
    private Long orderDetailId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /**
     * 入库批次号（V2101）：批次物料入库时透传给 StockService.stockIn 落批次行；
     * 空串=非批次物料汇总行（与 inv_stock.batch_no 语义一致）。
     * 采购退货审核时按该列以 BATCH 策略反向出库（从原入库批次退回）。
     */
    private String batchNo;

    /** 实收数量（>0） */
    private BigDecimal qty;

    /** 含税单价 */
    private BigDecimal price;

    /** 税率（百分数） */
    private BigDecimal taxRate;

    /** 含税金额 = qty × price，HALF_UP 2 位 */
    private BigDecimal amount;

    /** 本明细累计被退货数量（退货审核时累加） */
    private BigDecimal returnedQty;

    /** 备注 */
    private String remark;
}
