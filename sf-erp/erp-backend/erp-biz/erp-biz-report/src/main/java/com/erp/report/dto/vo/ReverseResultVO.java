package com.erp.report.dto.vo;

import lombok.Data;

/**
 * 通用红冲结果：生成的负数反向单信息（已同步完成反向过账）。
 */
@Data
public class ReverseResultVO {

    /** 红冲单据类型（与原单相同） */
    private String docType;

    /** 红冲单 ID */
    private Long reverseDocId;

    /** 红冲单号（原单号 + R） */
    private String reverseDocNo;

    /** 红冲金额（负数） */
    private java.math.BigDecimal reverseAmount;
}
