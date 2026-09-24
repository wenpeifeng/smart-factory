package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存库龄分析 VO：分桶汇总 + 明细。
 * <p>
 * 库龄锚点 = 该仓物料最后一次入库方向流水（inv_movement.qty &gt; 0）的时间；
 * 无入库流水的库存行归入「无入库流水」桶单独展示，不参与四个标准桶。
 */
@Data
public class InventoryAgingVO {

    /** 参与库龄分析的总库存量（qty &gt; 0 的库存行合计） */
    private BigDecimal totalQty;

    /** 分桶汇总（固定 4 个标准桶 + 可选「无入库流水」桶，按序返回） */
    private List<AgingBucketVO> buckets;

    /** 库龄明细（仓库×物料粒度） */
    private List<AgingDetailVO> details;

    /** 库龄分桶汇总行 */
    @Data
    public static class AgingBucketVO {
        /** 桶标识：0-30 / 31-90 / 91-180 / 180+ / NONE */
        private String bucket;
        /** 展示名：0-30天 / 31-90天 / 91-180天 / 180天以上 / 无入库流水 */
        private String label;
        /** 该桶库存量 */
        private BigDecimal qty;
        /** 占总量比例（0~1，4 位小数；总量为 0 时为 0） */
        private BigDecimal ratio;
    }

    /** 库龄明细行（仓库×物料） */
    @Data
    public static class AgingDetailVO {
        /** 仓库 ID */
        private Long warehouseId;
        /** 仓库名称 */
        private String warehouseName;
        /** 物料 ID */
        private Long materialId;
        /** 物料编码 */
        private String materialCode;
        /** 物料名称 */
        private String materialName;
        /** 现存量 */
        private BigDecimal qty;
        /** 最近加权价（元） */
        private BigDecimal price;
        /** 库存价值（元） */
        private BigDecimal value;
        /** 最后一次入库时间（yyyy-MM-dd HH:mm，无流水为 null） */
        private String lastInTime;
        /** 库龄天数（无流水为 null） */
        private Integer ageDays;
        /** 所属桶标识 */
        private String bucket;
        /** 所属桶展示名 */
        private String bucketLabel;
    }
}
