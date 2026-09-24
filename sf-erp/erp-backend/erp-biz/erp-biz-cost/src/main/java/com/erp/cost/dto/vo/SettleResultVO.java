package com.erp.cost.dto.vo;

import java.math.BigDecimal;

/**
 * 月末结账结果 VO（POST /cost/settle/{period}）。
 */
public record SettleResultVO(
        String period,
        String status,
        /** 本期出库成本合计 */
        BigDecimal outAmount,
        /** 其中：销售出库（转主营业务成本） */
        BigDecimal salOutCost,
        /** 其中：生产领料（转生产成本） */
        BigDecimal prodUseCost,
        /** 其他出库（OTH_OUT/PUR_RET/盘亏，转主营业务成本） */
        BigDecimal otherOutCost,
        /** 自动生成的转账凭证 ID（出库成本为 0 时为空） */
        Long voucherId,
        String voucherNo) {
}
