package com.erp.sales.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报价单行毛利率视图聚合 VO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuotationMarginBundle {

    /** 报价单号 */
    private String quotationNo;

    /** 有效状态（含动态 30已过期） */
    private Integer status;

    /** 毛利率警戒线（%） */
    private BigDecimal warnThreshold;

    /** 行毛利率明细 */
    private List<QuotationMarginRowVO> rows;
}
