package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 序列号库存（V422）—— 序列号管理物料的单品级全生命周期追溯。
 * <p>
 * 状态机：IN_STOCK 在库 →（出库核验）OUT 已出库；IN_STOCK → SCRAP 报废。
 * serial_no 全局唯一（uk_serial_no）；入库登记记 source_no，
 * 出库核销记 out_no，正查（按序列号）与反查（按物料）均不 JOIN 业务单据。
 * <p>
 * 序列号表是<b>追溯台账</b>，不参与 inv_stock 数量过账——
 * 数量账仍由 StockService 唯一维护（序列号核销与出库单审核是两次独立动作）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_serial")
public class InvSerial extends BaseEntity {

    /** 状态：在库 */
    public static final String STATUS_IN_STOCK = "IN_STOCK";
    /** 状态：已出库 */
    public static final String STATUS_OUT = "OUT";
    /** 状态：已报废 */
    public static final String STATUS_SCRAP = "SCRAP";

    /** 序列号（全局唯一） */
    private String serialNo;

    /** 物料 ID */
    private Long materialId;

    /** 当前/最后所在仓库 ID */
    private Long warehouseId;

    /** 批次号（可空串） */
    private String batchNo;

    /** 状态：IN_STOCK在库 OUT已出库 SCRAP报废 */
    private String status;

    /** 入库来源单号 */
    private String sourceNo;

    /** 出库单号（核销时写） */
    private String outNo;

    /** 备注 */
    private String remark;
}
