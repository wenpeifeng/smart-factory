package com.erp.qc.dto.query;

import lombok.Data;

/**
 * 不合格品处置单分页查询条件。
 */
@Data
public class QcRejectQuery {

    private long pageNum = 1;
    private long pageSize = 10;

    /** 关键词：处置单号/来源检验单号模糊 */
    private String keyword;

    /** 物料 ID */
    private Long materialId;

    /** 处置决定：1退货 2返工 3让步接收 4报废 */
    private Integer decision;
}
