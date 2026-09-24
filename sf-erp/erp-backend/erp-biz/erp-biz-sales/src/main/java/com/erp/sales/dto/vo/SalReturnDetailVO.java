package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售退货单明细行响应 VO。
 */
@Data
public class SalReturnDetailVO {

    /** 行 ID */
    private Long id;

    /** 退货单 ID */
    private Long returnId;

    /** 来源出库行 ID */
    private Long outDetailId;

    /** 物料 ID */
    private Long materialId;

    /** 退货入库批次号（批次物料回补指定批次；空=非批次物料汇总行） */
    private String batchNo;

    /** 物料编码（关联 base_material） */
    private String materialCode;

    /** 物料名称（关联 base_material） */
    private String materialName;

    /** 规格型号（关联 base_material） */
    private String materialSpec;

    /** 退货数量（正数） */
    private BigDecimal qty;

    /** 含税单价 */
    private BigDecimal price;

    /** 金额（正数） */
    private BigDecimal amount;

    /** 行备注 */
    private String remark;
}
