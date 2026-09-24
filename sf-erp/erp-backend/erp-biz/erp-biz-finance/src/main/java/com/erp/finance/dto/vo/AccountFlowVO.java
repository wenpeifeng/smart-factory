package com.erp.finance.dto.vo;

import com.erp.finance.entity.FinAccountFlow;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 账户流水 VO。
 *
 * @param id           流水 ID
 * @param accountId    账户 ID
 * @param bizDate      业务日期
 * @param bizType      业务类型：RECEIPT / PAYMENT
 * @param bizId        业务单据 ID
 * @param bizNo        业务单号
 * @param direction    收支方向：INCOME / EXPENSE
 * @param amount       发生金额
 * @param balanceAfter 记账后余额快照
 * @param remark       备注
 */
public record AccountFlowVO(Long id, Long accountId, LocalDate bizDate, String bizType,
                            Long bizId, String bizNo, String direction, BigDecimal amount,
                            BigDecimal balanceAfter, String remark) {

    /** 实体转 VO */
    public static AccountFlowVO from(FinAccountFlow f) {
        return new AccountFlowVO(f.getId(), f.getAccountId(), f.getBizDate(), f.getBizType(),
                f.getBizId(), f.getBizNo(), f.getDirection(), f.getAmount(),
                f.getBalanceAfter(), f.getRemark());
    }
}
