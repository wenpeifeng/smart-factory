package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 能力计划(CRP 简版)结果 VO —— 期间参数 + 各工作中心负荷行 + 超载列表。
 */
@Data
public class CapacityLoadResultVO implements Serializable {

    /** 统计期间起（含） */
    private LocalDate fromDate;

    /** 统计期间止（含） */
    private LocalDate toDate;

    /** 期间工作日数（不含周六周日） */
    private long workdays;

    /** 各工作中心负荷行 */
    private List<CapacityLoadVO> rows = new ArrayList<>();

    /** 超载（负荷率>100%）的工作中心行 */
    private List<CapacityLoadVO> overloads = new ArrayList<>();
}
