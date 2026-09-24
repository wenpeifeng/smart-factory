package com.erp.qc.dto.query;

import lombok.Data;

/**
 * 检验模板分页查询条件。
 */
@Data
public class QcTemplateQuery {

    private long pageNum = 1;
    private long pageSize = 10;

    /** 关键词：模板编码/名称模糊 */
    private String keyword;

    /** 检验类型：IQC/IPQC/FQC/OQC */
    private String inspectType;

    /** 状态：1启用 0停用 */
    private Integer status;
}
