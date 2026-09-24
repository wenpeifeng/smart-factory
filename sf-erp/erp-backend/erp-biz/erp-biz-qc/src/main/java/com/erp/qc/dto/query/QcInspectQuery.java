package com.erp.qc.dto.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 检验单分页查询条件。
 */
@Data
public class QcInspectQuery {

    private long pageNum = 1;
    private long pageSize = 10;

    /** 关键词：检验单号/来源单号模糊 */
    private String keyword;

    /** 检验类型：IQC/IPQC/FQC/OQC */
    private String inspectType;

    /** 来源单据类型：pur_instock/mrp_work_order/sal_return */
    private String sourceType;

    /** 物料 ID */
    private Long materialId;

    /** 检验结论：1合格 2让步接收 3退货 */
    private Integer conclusion;

    /** 状态：0草稿 10已检验 */
    private Integer status;

    /** 检验日期起（含） */
    private LocalDate dateBegin;

    /** 检验日期止（含） */
    private LocalDate dateEnd;
}
