package com.erp.invoice.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 供应商待登记发票清单行（GET /invoice/purchases/toBeRegistered）。
 * <p>
 * 口径：已审核（status=10）采购入库单 − 已存在未红冲蓝字发票（待认证/已认证）的入库单。
 *
 * @param instockId    入库单 ID
 * @param instockNo    入库单号
 * @param supplierId   供应商 ID
 * @param supplierName 供应商名称（可能为 null）
 * @param instockDate  入库日期
 * @param amount       不含税金额（单头 total_amount）
 */
public record ToBeRegisteredVO(Long instockId, String instockNo, Long supplierId, String supplierName,
                               LocalDate instockDate, BigDecimal amount) {
}
