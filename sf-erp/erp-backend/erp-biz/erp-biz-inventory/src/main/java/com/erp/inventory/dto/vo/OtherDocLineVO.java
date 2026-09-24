package com.erp.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 其他入库/出库单明细行 VO（详情用）。
 */
@Data
public class OtherDocLineVO {

    private Long id;

    private Long materialId;
    private String materialCode;
    private String materialName;

    /** 批次号（非批次物料为空串） */
    private String batchNo;

    /** 数量（正数） */
    private BigDecimal qty;

    /** 行备注 */
    private String remark;
}
