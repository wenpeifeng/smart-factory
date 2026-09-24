package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MRP 运算展开明细树节点（按批次查看全树，前端 AntD Table 树形直接消费 children）。
 */
@Data
public class CalcDetailTreeVO implements Serializable {

    /** 树节点路径（组树键） */
    private String nodePath;

    /** BOM层级 */
    private Integer level;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String code;

    /** 物料名称 */
    private String name;

    /** 计量单位 */
    private String unit;

    /** 毛需求 */
    private BigDecimal grossQty;

    /** 现有库存快照 */
    private BigDecimal stockQty;

    /** 在途快照 */
    private BigDecimal onwayQty;

    /** 净需求 */
    private BigDecimal netQty;

    /** 建议数量 */
    private BigDecimal suggestQty;

    /** 建议日期 */
    private LocalDate suggestDate;

    /** 建议类型 */
    private String planType;

    /** 子节点 */
    private List<CalcDetailTreeVO> children = new ArrayList<>();
}
