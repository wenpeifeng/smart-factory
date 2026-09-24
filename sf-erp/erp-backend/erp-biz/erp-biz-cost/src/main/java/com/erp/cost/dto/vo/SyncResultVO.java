package com.erp.cost.dto.vo;

import java.math.BigDecimal;

/**
 * 计价重算结果 VO（POST /cost/sync）。
 */
public record SyncResultVO(
        /** 实际重算起始期间（已结账期间之后的第一个月） */
        String fromPeriod,
        String toPeriod,
        /** 重放的库存流水条数 */
        int movementsProcessed,
        /** 生成（重写）的计价流水条数 */
        int flowsInserted,
        /** 涉及物料数 */
        int materials) {
}
