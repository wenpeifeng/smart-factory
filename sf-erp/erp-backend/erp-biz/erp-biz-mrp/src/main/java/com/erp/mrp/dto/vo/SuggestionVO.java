package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MRP 发补建议 VO（含物料编码/名称回显）。
 */
@Data
public class SuggestionVO implements Serializable {

    /** 建议ID */
    private Long id;

    /** 运算批次号 */
    private String batchNo;

    /** 建议类型：PURCHASE/PRODUCE */
    private String planType;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 计量单位 */
    private String unit;

    /** 建议数量 */
    private BigDecimal qty;

    /** 建议日期 */
    private LocalDate suggestDate;

    /** BOM层级 */
    private Integer level;

    /** 状态：PENDING/CONVERTED */
    private String status;

    /** 转单引用类型 */
    private String refType;

    /** 转单引用ID */
    private Long refId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
