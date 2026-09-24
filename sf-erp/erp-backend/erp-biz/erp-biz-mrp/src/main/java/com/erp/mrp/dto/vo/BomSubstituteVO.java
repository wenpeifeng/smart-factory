package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * BOM 子件替代料 VO（含替代料/主料物料回显，V621）。
 */
@Data
public class BomSubstituteVO implements Serializable {

    /** 替代组行ID */
    private Long id;

    /** BOM 子件行ID（mrp_bom_item.id） */
    private Long bomItemId;

    /** BOMID（回显用） */
    private Long bomId;

    /** 主料物料ID */
    private Long mainMaterialId;

    /** 主料编码 */
    private String mainMaterialCode;

    /** 主料名称 */
    private String mainMaterialName;

    /** 替代料物料ID */
    private Long subMaterialId;

    /** 替代料编码 */
    private String subMaterialCode;

    /** 替代料名称 */
    private String subMaterialName;

    /** 计量单位（替代料） */
    private String unit;

    /** 优先级（1最高） */
    private Integer priority;

    /** 用量换算比（1单位主料 = ratio 单位替代料） */
    private BigDecimal ratio;

    /** 备注 */
    private String remark;
}
