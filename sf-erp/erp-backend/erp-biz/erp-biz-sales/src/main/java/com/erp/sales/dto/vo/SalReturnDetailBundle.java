package com.erp.sales.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 销售退货单详情聚合 VO：表头 + 明细行。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalReturnDetailBundle {

    /** 表头 */
    private SalReturnVO ret;

    /** 明细行 */
    private List<SalReturnDetailVO> details;
}
