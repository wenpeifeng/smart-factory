package com.erp.finance.dto.vo;

import com.erp.finance.entity.FinPayable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应付单 VO（供应商名称查询 base_supplier 得到，表未落库时为 null）。
 *
 * @param id              应付单 ID
 * @param supplierId      供应商 ID
 * @param supplierName    供应商名称（可能为 null）
 * @param bizNo           来源业务单号
 * @param bizDate         业务日期
 * @param dueDate         到期日
 * @param amount          应付金额
 * @param settledAmount   已核销金额
 * @param unsettledAmount 未核销余额
 * @param status          状态：UNSETTLED / PARTIAL / SETTLED
 * @param remark          备注
 * @param createTime      创建时间
 */
public record PayableVO(Long id, Long supplierId, String supplierName, String bizNo,
                        LocalDate bizDate, LocalDate dueDate, BigDecimal amount,
                        BigDecimal settledAmount, BigDecimal unsettledAmount, String status,
                        String remark, LocalDateTime createTime) {

    /** 实体转 VO（供应商名称由 Service 层批量回填） */
    public static PayableVO from(FinPayable p, String supplierName) {
        return new PayableVO(p.getId(), p.getSupplierId(), supplierName, p.getBizNo(),
                p.getBizDate(), p.getDueDate(), p.getAmount(), p.getSettledAmount(),
                p.getAmount().subtract(p.getSettledAmount()), p.getStatus(),
                p.getRemark(), p.getCreateTime());
    }
}
