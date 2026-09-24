package com.erp.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 盘点明细行 VO（详情/录入实盘用）。
 */
@Data
public class CheckDetailVO {

    private Long id;

    private Long materialId;
    private String materialCode;
    private String materialName;

    /** 批次号（快照自 inv_stock；非批次物料为空串） */
    private String batchNo;

    /** 库位 ID（快照自 inv_stock.location_id，0=未上架行；V421） */
    private Long locationId;

    /** 库位编码（location_id=0 时为 null） */
    private String locationCode;

    /** 账面数量（快照） */
    private BigDecimal bookQty;

    /** 实盘数量（未录入为 null） */
    private BigDecimal actualQty;

    /** 差异 = 实盘 - 账面（未录入为 null） */
    private BigDecimal diffQty;
}
