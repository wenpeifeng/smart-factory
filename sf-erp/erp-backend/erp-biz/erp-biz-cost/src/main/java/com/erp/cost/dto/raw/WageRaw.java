package com.erp.cost.dto.raw;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 计件工资只读投影（hr_piece_wage_detail 约定表，HR 二期并行开发中）。
 * <p>
 * 约定结构：hr_piece_wage_detail(work_order_id, employee_id, amount)，
 * HR 模块按开发规范另含审计字段（deleted 等）。
 */
@Data
public class WageRaw {

    /** 行 ID */
    private Long id;

    /** 生产订单 ID */
    private Long workOrderId;

    /** 员工 ID */
    private Long employeeId;

    /** 计件工资金额 */
    private BigDecimal amount;

    /** 明细行数（汇总查询时的 COUNT 投影） */
    private Integer lineCount;
}
