package com.erp.hr.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计件工资期间 VO —— 附带期间合计金额（聚合明细）。
 */
@Data
public class PieceWageVO {

    private Long id;

    /** 期间（yyyy-MM） */
    private String period;

    /** 状态：OPEN/CLOSED */
    private String status;

    /** 明细条数 */
    private Long detailCount;

    /** 期间计件总金额 */
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
