package com.erp.sales.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 销售报价单详情聚合 VO：表头 + 明细行。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalQuotationDetailBundle {

    /** 表头 */
    private SalQuotationVO quotation;

    /** 明细行 */
    private List<SalQuotationItemVO> items;
}
