package com.erp.basedata.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 多供应商同物料价格对比行（{@code PurPriceRecordMapper#compareByMaterial} 映射）。
 * <p>
 * 供应商名称与生效合同价由 Service 填充。
 */
@Data
public class PriceCompareRowVO {

    /** 供应商 ID */
    private Long supplierId;

    /** 供应商名称（冗余，Service 填充） */
    private String supplierName;

    /** 成交次数 */
    private Long times;

    /** 最低价 */
    private BigDecimal minPrice;

    /** 最高价 */
    private BigDecimal maxPrice;

    /** 均价（4 位） */
    private BigDecimal avgPrice;

    /** 最近一次成交价 */
    private BigDecimal lastPrice;

    /** 最近成交日期 */
    private LocalDate lastDate;

    /** 生效合同价（Service 填充，无合同为 null） */
    private BigDecimal contractPrice;
}
