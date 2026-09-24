package com.erp.invoice.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 客户/供应商对账辅助 VO（GET /invoice/reconciliation）。
 * <p>
 * 口径（不含税）：业务金额 = 期间内已审核出库/入库单金额合计；
 * 开票金额 = 期间内已生效发票（含红字负数）金额合计；
 * 差额 = 业务金额 − 开票金额，正数即未开票部分。
 *
 * @param partnerType    往来类型：customer-客户 supplier-供应商
 * @param partnerId      往来单位 ID
 * @param partnerName    往来单位名称（可能为 null）
 * @param from           期间起（含）
 * @param to             期间止（含）
 * @param bizOrderCount  业务单据数
 * @param bizAmount      业务金额（不含税）
 * @param invoiceCount   已生效发票数（含红字票）
 * @param invoicedAmount 开票金额（不含税，净额）
 * @param diffAmount     未开票差额 = 业务金额 − 开票金额
 */
public record ReconciliationVO(String partnerType, Long partnerId, String partnerName,
                               LocalDate from, LocalDate to,
                               long bizOrderCount, BigDecimal bizAmount,
                               long invoiceCount, BigDecimal invoicedAmount, BigDecimal diffAmount) {
}
