package com.erp.report.dto.vo;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 材料损耗分析 VO（工单 × 领料物料）。
 * <p>
 * 应耗 = 已审核领料单明细 should_qty（BOM 应耗）合计；
 * 实耗 = actual_qty 合计；超耗 = 实耗 − 应耗；超耗率 = 超耗 / 应耗 × 100（2 位）。
 * 应耗为 0 的行无法计算超耗率，SQL 层已跳过。
 */
@Data
public class MaterialLossVO {

    /** 工单 × 物料行（按工单号/物料编码排序） */
    private List<Row> rows;

    /** 合计行 */
    private Total total;

    /** 单行损耗 */
    @Data
    public static class Row {

        private Long workOrderId;

        private String orderNo;

        private Long materialId;

        private String materialCode;

        private String materialName;

        /** BOM 应耗数量 */
        private BigDecimal shouldQty;

        /** 实际领料数量 */
        private BigDecimal actualQty;

        /** 超耗 = actualQty − shouldQty（负数为节约） */
        private BigDecimal lossQty;

        /** 超耗率（%，2 位 HALF_UP；应耗 0 → 0） */
        private BigDecimal lossRate;
    }

    /** 合计行 */
    @Data
    public static class Total {

        private BigDecimal shouldQty;

        private BigDecimal actualQty;

        private BigDecimal lossQty;

        /** 超耗率（%，合计口径） */
        private BigDecimal lossRate;
    }
}
