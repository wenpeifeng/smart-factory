package com.erp.sales.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 销售订单详情聚合 VO：表头 + 明细行。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalOrderDetailBundle {

    /** 表头 */
    private SalOrderVO order;

    /** 明细行 */
    private List<SalOrderDetailVO> details;
}
