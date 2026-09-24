package com.erp.hr.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 计件归集结果 VO —— 命中/跳过计数保证归集过程可审计。
 */
@Data
public class PieceWageCollectResultVO {

    /** 期间ID */
    private Long periodId;

    /** 期间（yyyy-MM） */
    private String period;

    /** 候选完工订单数 */
    private int candidateOrders;

    /** 生成明细条数 */
    private int detailCount;

    /** 归集金额合计 */
    private BigDecimal totalAmount;

    /** 跳过：物料无启用价目 */
    private int skippedNoPrice;

    /** 跳过：工单制单人未关联在职员工 */
    private int skippedNoEmployee;
}
