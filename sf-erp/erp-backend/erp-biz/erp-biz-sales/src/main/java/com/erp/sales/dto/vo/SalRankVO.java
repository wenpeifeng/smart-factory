package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售排行 TOP10 行 VO（GET /sales/statistics/rank?type=material|customer）。
 */
@Data
public class SalRankVO {

    /** 维度对象 ID（物料 ID 或客户 ID） */
    private Long refId;

    /** 维度对象编码（物料编码或客户编码） */
    private String refCode;

    /** 维度对象名称（物料名称或客户名称） */
    private String refName;

    /** 销售额（已审核出库单金额合计，降序排列依据） */
    private BigDecimal salesAmount;

    /** 销售数量合计（按物料维度时有值） */
    private BigDecimal salesQty;
}
