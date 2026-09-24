package com.erp.inventory.dto.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 物料 ABC 分级行 VO（V421）。
 */
@Data
public class AbcClassVO {

    private Long id;

    private Long materialId;
    private String materialCode;
    private String materialName;

    /** 分级：A/B/C */
    private String level;

    /** 近 90 天出库频次 */
    private Long freq;

    /** 分级计算日期 */
    private LocalDate calcDate;
}
