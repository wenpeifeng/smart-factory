package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * MRP 运算批次的全树展开明细（按 MPS 根分组的多棵树）。
 */
@Data
public class BatchDetailVO implements Serializable {

    /** 运算批次号 */
    private String batchNo;

    /** 展开树（每个 MPS 成品一棵，level=0 为根） */
    private List<CalcDetailTreeVO> trees;
}
