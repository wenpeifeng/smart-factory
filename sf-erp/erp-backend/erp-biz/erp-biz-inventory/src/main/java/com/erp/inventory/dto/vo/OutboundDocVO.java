package com.erp.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 建波可选出库单行 VO（V421）：已审核且未全部生成拣选任务的出库单。
 */
@Data
public class OutboundDocVO {

    /** 出库单 ID */
    private Long docId;

    /** 来源类型：SAL_OUT 销售出库 / OTH_OUT 其他出库 */
    private String sourceType;

    /** 出库单号 */
    private String docNo;

    private Long warehouseId;

    /** 行数 */
    private Long lineCount;

    /** 总数量 */
    private BigDecimal totalQty;

    /** 审核时间 */
    private LocalDateTime auditTime;
}
