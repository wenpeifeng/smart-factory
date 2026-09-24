package com.erp.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 其他入库/出库单列表行 VO（两类单据共用结构）。
 */
@Data
public class OtherDocVO {

    private Long id;

    /** 单号（OI.../OO...） */
    private String no;

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;

    /** 入/出库原因 */
    private String reason;

    /** 状态：0草稿 1已审核 */
    private Integer status;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 备注 */
    private String remark;

    /** 行数 */
    private Long lineCount;

    /** 总数量（各行 qty 合计） */
    private BigDecimal totalQty;

    /** 创建时间 */
    private LocalDateTime createTime;
}
