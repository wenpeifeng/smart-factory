package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * BOM 多级展开树节点（GET /mrp/boms/{id}/tree 返回结构，递归 children）。
 */
@Data
public class BomTreeVO implements Serializable {

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String code;

    /** 物料名称 */
    private String name;

    /** 计量单位 */
    private String unit;

    /** 层级（0=成品根节点） */
    private Integer level;

    /** 单位用量（相对父件，根节点为 1） */
    private BigDecimal qty;

    /** 损耗率% */
    private BigDecimal lossRate;

    /** 展开所用的 BOM 版本（叶子采购件为空） */
    private String bomVersion;

    /** 子节点 */
    private List<BomTreeVO> children = new ArrayList<>();
}
