package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 主生产计划 VO（含物料编码/名称回显）。
 */
@Data
public class MpsVO implements Serializable {

    /** MPS行ID */
    private Long id;

    /** 成品物料ID */
    private Long materialId;

    /** 成品物料编码 */
    private String materialCode;

    /** 成品物料名称 */
    private String materialName;

    /** 计量单位 */
    private String unit;

    /** 计划数量 */
    private BigDecimal qty;

    /** 计划开工日期 */
    private LocalDate startDate;

    /** 状态：PENDING/CALCULATED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
