package com.erp.qc.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 质量统计 VO：按物料/供应商的合格率 + 检验单量趋势。
 * <p>
 * 口径：仅统计已检验（status=10）检验单，按检验日期落在 [from, to]；
 * 合格率 = Σ合格数 / Σ抽检数 × 100（HALF_UP 2 位，抽检数为 0 时记 0）。
 */
@Data
public class QcStatisticsVO {

    /** 统计区间起（含） */
    private LocalDate fromDate;

    /** 统计区间止（含） */
    private LocalDate toDate;

    /** 按物料合格率（抽检量降序） */
    private List<MaterialStatVO> materialStats = new ArrayList<>();

    /** 按供应商合格率（仅带供应商的 IQC 口径，抽检量降序） */
    private List<SupplierStatVO> supplierStats = new ArrayList<>();

    /** 检验单量趋势（按天，含无单据的日期补零） */
    private List<TrendPointVO> trend = new ArrayList<>();

    /**
     * 按物料汇总行。
     */
    @Data
    public static class MaterialStatVO {

        /** 物料 ID */
        private Long materialId;

        /** 物料编码 */
        private String materialCode;

        /** 物料名称 */
        private String materialName;

        /** 累计抽检数量 */
        private BigDecimal sampleQty;

        /** 累计合格数量 */
        private BigDecimal qualifiedQty;

        /** 合格率 %（0-100，HALF_UP 2 位） */
        private BigDecimal passRate;

        /** 计算并回填合格率（抽检为 0 时记 0，防除零） */
        public void fillPassRate() {
            this.passRate = passRateOf(this.sampleQty, this.qualifiedQty);
        }
    }

    /**
     * 按供应商汇总行。
     */
    @Data
    public static class SupplierStatVO {

        /** 供应商 ID */
        private Long supplierId;

        /** 供应商名称 */
        private String supplierName;

        /** 累计抽检数量 */
        private BigDecimal sampleQty;

        /** 累计合格数量 */
        private BigDecimal qualifiedQty;

        /** 合格率 %（0-100，HALF_UP 2 位） */
        private BigDecimal passRate;

        /** 计算并回填合格率（抽检为 0 时记 0，防除零） */
        public void fillPassRate() {
            this.passRate = passRateOf(this.sampleQty, this.qualifiedQty);
        }
    }

    /**
     * 趋势点（一天一条）。
     */
    @Data
    public static class TrendPointVO {

        /** 日期 */
        private LocalDate date;

        /** 当日检验单量（已检验） */
        private Long count;

        /** 当日抽检数量合计 */
        private BigDecimal sampleQty;
    }

    /**
     * 卡点未完成检验单（pending 接口展示用）。
     */
    @Data
    public static class PendingInspectVO {

        /** 检验单 ID */
        private Long id;

        /** 检验单号 */
        private String inspectNo;

        /** 状态（卡点查询恒为 0草稿） */
        private Integer status;
    }

    /** 合格率计算：合格/抽检 ×100，HALF_UP 2 位，抽检为空/0 记 0 */
    static BigDecimal passRateOf(BigDecimal sampleQty, BigDecimal qualifiedQty) {
        if (sampleQty == null || qualifiedQty == null || sampleQty.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return qualifiedQty.multiply(BigDecimal.valueOf(100))
                .divide(sampleQty, 2, RoundingMode.HALF_UP);
    }
}
