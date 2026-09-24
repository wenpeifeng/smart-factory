package com.mes.plan.dto.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 生产工单分页查询对象。
 */
@Data
public class WorkorderPageQuery {
    /** 页码，从 1 开始 */
    private long pageNum = 1;
    /** 每页条数 */
    private long pageSize = 10;
    /** 关键字（工单号/来源单号 模糊） */
    private String keyword;
    /** 状态 */
    private String status;
    /** 成品物料 ID */
    private Long materialId;
    /** 计划开工日期起 */
    private LocalDate planDateFrom;
    /** 计划开工日期止 */
    private LocalDate planDateTo;
}
