package com.erp.invoice.dto.vo;

import com.erp.invoice.entity.InvPurchaseInvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 进项发票 VO（供应商名称由 Service 层批量回填，表未落库时为 null）。
 *
 * @param id              发票 ID
 * @param invoiceNo       发票号码（8 位）
 * @param invoiceDate     开票日期
 * @param supplierId      销方供应商 ID
 * @param supplierName    销方供应商名称（可能为 null）
 * @param bizType         业务单据类型
 * @param bizId           业务单据 ID
 * @param bizNo           业务单号
 * @param amount          金额（不含税）
 * @param taxRate         税率（%）
 * @param taxAmount       税额（红字为负）
 * @param totalAmount     价税合计（红字为负）
 * @param redFlag         红字标志：0-蓝字 1-红字
 * @param sourceInvoiceNo 关联原蓝字发票号码（红字票才有值）
 * @param status          状态：0待认证 10已认证 20已红冲
 * @param authDate        认证日期
 * @param remark          备注
 * @param createTime      创建时间
 */
public record PurchaseInvoiceVO(Long id, String invoiceNo, LocalDate invoiceDate,
                                Long supplierId, String supplierName, String bizType, Long bizId, String bizNo,
                                BigDecimal amount, BigDecimal taxRate, BigDecimal taxAmount, BigDecimal totalAmount,
                                Integer redFlag, String sourceInvoiceNo, Integer status,
                                LocalDate authDate, String remark, LocalDateTime createTime) {

    /** 实体转 VO（供应商名称由 Service 层批量回填） */
    public static PurchaseInvoiceVO from(InvPurchaseInvoice e, String supplierName) {
        return new PurchaseInvoiceVO(e.getId(), e.getInvoiceNo(), e.getInvoiceDate(),
                e.getSupplierId(), supplierName, e.getBizType(), e.getBizId(), e.getBizNo(),
                e.getAmount(), e.getTaxRate(), e.getTaxAmount(), e.getTotalAmount(),
                e.getRedFlag(), e.getSourceInvoiceNo(), e.getStatus(),
                e.getAuthDate(), e.getRemark(), e.getCreateTime());
    }
}
