package com.erp.invoice.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售出库单只读快照（跨模块查询 sal_outstock 的原始行，见 InvQueryMapper.xml）。
 *
 * @param id          出库单 ID
 * @param outNo       出库单号
 * @param customerId  客户 ID
 * @param outDate     出库日期
 * @param totalAmount 单头金额（不含税）
 * @param status      状态：0草稿 10已审核
 */
public record OutstockRawVO(Long id, String outNo, Long customerId, LocalDate outDate,
                            BigDecimal totalAmount, Integer status) {
}
