package com.erp.cost.dto.vo;

import java.math.BigDecimal;

/**
 * 工单计件工资明细行 VO（工）。
 */
public record WageLineVO(
        Long id,
        Long employeeId,
        BigDecimal amount) {
}
