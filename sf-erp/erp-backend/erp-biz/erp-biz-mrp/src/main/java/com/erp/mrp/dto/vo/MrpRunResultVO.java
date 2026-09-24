package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * MRP 运算结果摘要。
 */
@Data
public class MrpRunResultVO implements Serializable {

    /** 运算批次号 */
    private String batchNo;

    /** 参与 MPS 行数 */
    private Integer mpsCount;

    /** 展开明细行数 */
    private Integer detailCount;

    /** 产出建议条数 */
    private Integer suggestionCount;
}
