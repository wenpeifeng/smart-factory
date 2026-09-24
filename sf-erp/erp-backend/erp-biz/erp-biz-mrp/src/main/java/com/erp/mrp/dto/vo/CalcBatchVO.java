package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MRP 运算批次 VO。
 */
@Data
public class CalcBatchVO implements Serializable {

    /** 批次ID */
    private Long id;

    /** 运算批次号 */
    private String batchNo;

    /** 参与运算的 MPS 行数 */
    private Integer mpsCount;

    /** 产出建议条数 */
    private Integer suggestionCount;

    /** 备注 */
    private String remark;

    /** 运算时间 */
    private LocalDateTime createTime;
}
