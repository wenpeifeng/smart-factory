package com.erp.invoice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销项发票实体（表 inv_sales_invoice）。
 * <p>
 * 生命周期：开票申请（从销售出库单生成，状态=待开票）→ 开票（已开票，写开票日期/开票人）
 * → 红冲（原票置为已红冲，同时插入一条负数红字票 {@link #redFlag}=1 关联原票）。
 * <p>
 * 金额不变量：tax_amount = amount × tax_rate% 四舍五入 HALF_UP 2 位；
 * total_amount = amount + tax_amount；红字票三个金额列一律取负。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_sales_invoice")
public class InvSalesInvoice extends BaseEntity {

    /** 发票号码（8 位数字，申请开票时预分配，红字票另行取号） */
    private String invoiceNo;

    /** 开票日期（待开票为 NULL，开票/红冲时写入） */
    private LocalDate invoiceDate;

    /** 购方客户 ID（base_customer.id） */
    private Long customerId;

    /** 业务单据类型（SAL_OUTSTOCK-销售出库单） */
    private String bizType;

    /** 业务单据 ID（sal_outstock.id） */
    private Long bizId;

    /** 业务单号（sal_outstock.out_no） */
    private String bizNo;

    /** 金额（不含税） */
    private BigDecimal amount;

    /** 税率（百分数：13.00 / 9.00 / 6.00） */
    private BigDecimal taxRate;

    /** 税额 = 金额 × 税率% HALF_UP 2 位（红字为负） */
    private BigDecimal taxAmount;

    /** 价税合计 = 金额 + 税额（红字为负） */
    private BigDecimal totalAmount;

    /** 红字标志：0-蓝字 1-红字（负数） */
    private Integer redFlag;

    /** 红字票关联的原蓝字发票 ID（蓝字为 NULL） */
    private Long sourceInvoiceId;

    /** 红字票关联的原蓝字发票号码（蓝字为 NULL） */
    private String sourceInvoiceNo;

    /** 状态：0-待开票 10-已开票 20-已红冲 */
    private Integer status;

    /** 开票人（开票 / 红冲操作人） */
    private Long issueBy;

    /** 开票时间 */
    private LocalDateTime issueTime;

    /** 备注 */
    private String remark;
}
