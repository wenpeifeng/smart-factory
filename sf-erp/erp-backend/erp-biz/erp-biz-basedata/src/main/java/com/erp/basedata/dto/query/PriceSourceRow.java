package com.erp.basedata.dto.query;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 价格来源扫描行（{@code PriceSourceMapper} 手写 SQL 的结果映射）。
 * <p>
 * taxRate 为 NULL 表示来源价已是不含税裸价（RFQ 定标价），直接入库；
 * 否则按 taxPrice / (1 + taxRate/100) 换算不含税。
 */
@Data
public class PriceSourceRow {

    /** 来源单号（PO/RK/IN 前缀） */
    private String sourceNo;

    /** 供应商 ID */
    private Long supplierId;

    /** 价格日期（单据业务日期） */
    private LocalDate priceDate;

    /** 来源明细行 ID（幂等键） */
    private Long sourceDetailId;

    /** 物料 ID */
    private Long materialId;

    /** 来源单价（订单/入库=含税；RFQ=不含税定标价） */
    private BigDecimal taxPrice;

    /** 税率 %（NULL=来源价已不含税） */
    private BigDecimal taxRate;
}
