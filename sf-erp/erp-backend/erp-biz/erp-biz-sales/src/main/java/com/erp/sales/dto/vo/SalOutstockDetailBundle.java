package com.erp.sales.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 销售出库单详情聚合 VO：表头 + 明细行。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalOutstockDetailBundle {

    /** 表头 */
    private SalOutstockVO outstock;

    /** 明细行 */
    private List<SalOutstockDetailVO> details;
}
