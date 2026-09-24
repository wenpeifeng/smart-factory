package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库存盘点明细行 —— 生成盘点单时按 inv_stock 抄入的快照 + 实盘录入 + 差异。
 * <p>
 * diffQty = actualQty - bookQty；完成盘点时按 diffQty 调平库存
 * （正数盘盈入库、负数盘亏出库）。actualQty 为 NULL 表示该行未盘点。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_check_detail")
public class InvCheckDetail extends BaseEntity {

    /** 头表 ID（inv_check.id） */
    private Long checkId;

    /** 物料 ID */
    private Long materialId;

    /** 批次号（快照自 inv_stock.batch_no；非批次物料为空串，V411 起） */
    private String batchNo;

    /** 库位 ID（快照自 inv_stock.location_id，0=未上架行；V421 起） */
    private Long locationId;

    /** 账面数量（生成快照时的库存） */
    private BigDecimal bookQty;

    /** 实盘数量（未录入为 NULL） */
    private BigDecimal actualQty;

    /** 差异 = 实盘 - 账面（录入实盘时计算） */
    private BigDecimal diffQty;
}
