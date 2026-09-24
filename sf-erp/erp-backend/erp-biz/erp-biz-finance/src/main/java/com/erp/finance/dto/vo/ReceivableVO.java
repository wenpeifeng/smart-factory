package com.erp.finance.dto.vo;

import com.erp.finance.entity.FinReceivable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应收单 VO（客户名称查询 base_customer 得到，表未落库时为 null，前端展示客户 ID）。
 *
 * @param id              应收单 ID
 * @param customerId      客户 ID
 * @param customerName    客户名称（可能为 null）
 * @param bizNo           来源业务单号
 * @param bizDate         业务日期
 * @param dueDate         到期日
 * @param amount          应收金额
 * @param settledAmount   已核销金额
 * @param unsettledAmount 未核销余额（amount - settled_amount）
 * @param status          状态：UNSETTLED / PARTIAL / SETTLED
 * @param remark          备注
 * @param createTime      创建时间
 */
public record ReceivableVO(Long id, Long customerId, String customerName, String bizNo,
                           LocalDate bizDate, LocalDate dueDate, BigDecimal amount,
                           BigDecimal settledAmount, BigDecimal unsettledAmount, String status,
                           String remark, LocalDateTime createTime) {

    /** 实体转 VO（客户名称由 Service 层批量回填） */
    public static ReceivableVO from(FinReceivable r, String customerName) {
        return new ReceivableVO(r.getId(), r.getCustomerId(), customerName, r.getBizNo(),
                r.getBizDate(), r.getDueDate(), r.getAmount(), r.getSettledAmount(),
                r.getAmount().subtract(r.getSettledAmount()), r.getStatus(),
                r.getRemark(), r.getCreateTime());
    }
}
