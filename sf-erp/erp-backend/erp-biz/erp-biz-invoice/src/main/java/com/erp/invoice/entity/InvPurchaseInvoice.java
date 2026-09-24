package com.erp.invoice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 进项发票实体（表 inv_purchase_invoice）。
 * <p>
 * 生命周期：登记（从采购入库单生成，状态=待认证，记录票面发票号与开票日期）
 * → 认证（已认证，写认证日期/认证人，税额自此进入税负统计）
 * → 红冲（原票置为已红冲，同时插入一条负数红字票，红字票直接为已认证状态，
 * 认证日期取红冲当日，使已认证进项税额自然冲减）。
 * <p>
 * 金额不变量与销项一致：tax_amount = amount × tax_rate% HALF_UP 2 位；
 * total_amount = amount + tax_amount；红字票取负。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_purchase_invoice")
public class InvPurchaseInvoice extends BaseEntity {

    /** 发票号码（8 位数字：供应商票面号优先，否则系统生成） */
    private String invoiceNo;

    /** 开票日期（登记时写入，默认取入库日期） */
    private LocalDate invoiceDate;

    /** 销方供应商 ID（base_supplier.id） */
    private Long supplierId;

    /** 业务单据类型（PUR_INSTOCK-采购入库单） */
    private String bizType;

    /** 业务单据 ID（pur_instock.id） */
    private Long bizId;

    /** 业务单号（pur_instock.instock_no） */
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

    /** 状态：0-待认证 10-已认证 20-已红冲 */
    private Integer status;

    /** 认证日期（进项税额归属期间依据，认证/红冲时写入） */
    private LocalDate authDate;

    /** 认证人 */
    private Long authBy;

    /** 备注 */
    private String remark;
}
