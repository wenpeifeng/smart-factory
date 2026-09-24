package com.erp.invoice.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购入库单只读快照（跨模块查询 pur_instock 的原始行，见 InvQueryMapper.xml）。
 *
 * @param id           入库单 ID
 * @param instockNo    入库单号
 * @param supplierId   供应商 ID
 * @param instockDate  入库日期
 * @param totalAmount  单头金额（不含税）
 * @param status       状态：0草稿 10已审核
 */
public record InstockRawVO(Long id, String instockNo, Long supplierId, LocalDate instockDate,
                           BigDecimal totalAmount, Integer status) {
}
