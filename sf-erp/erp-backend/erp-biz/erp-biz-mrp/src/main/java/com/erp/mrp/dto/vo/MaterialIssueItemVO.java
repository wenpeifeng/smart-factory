package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 生产领料明细 VO（含物料编码/名称回显）。
 */
@Data
public class MaterialIssueItemVO implements Serializable {

    /** 明细ID */
    private Long id;

    /** 子件物料ID */
    private Long materialId;

    /** 子件物料编码 */
    private String materialCode;

    /** 子件物料名称 */
    private String materialName;

    /** 计量单位 */
    private String unit;

    /** 应领数量 */
    private BigDecimal shouldQty;

    /** 实领数量 */
    private BigDecimal actualQty;
}
