package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 多维报表通用行（销售明细 / 采购明细共用）。
 * <p>
 * 维度列随 groupBy 变化：
 * <ul>
 *   <li>material：dimCode=物料编码，dimName=物料名称；</li>
 *   <li>customer/supplier：dimCode=客商编码，dimName=客商名称；</li>
 *   <li>month：dimCode=dimName=yyyy-MM。</li>
 * </ul>
 * costAmount/profitAmount 仅销售明细有值（成本 = 数量 × base_material.ref_cost 参考成本，
 * 毛利 = 不含税额 − 成本）；采购明细恒为 null。
 */
@Data
public class ReportRowVO {

    /** 维度编码 */
    private String dimCode;

    /** 维度名称 */
    private String dimName;

    /** 数量合计 */
    private BigDecimal qty;

    /** 金额合计（销售=不含税额；采购=含税价税合计） */
    private BigDecimal amount;

    /** 参考成本合计（仅销售明细） */
    private BigDecimal costAmount;

    /** 毛利合计（仅销售明细，= amount − costAmount） */
    private BigDecimal profitAmount;
}
