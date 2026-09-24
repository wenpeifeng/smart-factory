package com.erp.report.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 经营驾驶舱聚合 VO。
 * <p>
 * 金额/数量全部 {@link BigDecimal}（DECIMAL 精度直传，前端按需格式化）；
 * 「本期」= 自然月（当月 1 日 ~ 次月 1 日，半开区间），环比对象 = 上一个自然月整月。
 * 库存总值 / 应收 / 应付为当前时点快照，无环比字段。
 */
@Data
public class DashboardVO {

    /** 本期月份，格式 yyyy-MM */
    private String month;

    /** 环比（上一个自然月），格式 yyyy-MM */
    private String prevMonth;

    /** 本期销售额（已审核销售出库，不含税口径，元） */
    private BigDecimal salesAmount;

    /** 上月销售额（环比基数） */
    private BigDecimal salesAmountPrev;

    /** 本期采购额（已审核采购入库，价税合计含税口径，元） */
    private BigDecimal purchaseAmount;

    /** 上月采购额（环比基数） */
    private BigDecimal purchaseAmountPrev;

    /** 库存总值（inv_stock × 最近加权价，时点值，元） */
    private BigDecimal inventoryValue;

    /** 应收余额（SUM(amount−settled_amount)，时点值，元） */
    private BigDecimal receivableBalance;

    /** 应付余额（时点值，元） */
    private BigDecimal payableBalance;

    /** 待办审批数（wf_task PENDING；约定表未上线时为 0） */
    private Long pendingApprovalCount;

    /** 库存预警数（与 /inventory/warnings 同口径的行数） */
    private Long stockWarningCount;

    /** 本月生产完工数（本月有完工动作的工单 finish_qty 合计，含部分完工） */
    private BigDecimal productionFinishQty;

    /** 上月生产完工数（环比基数） */
    private BigDecimal productionFinishQtyPrev;

    /** 近 6 个月销售/采购月度走势（无单据月份补零） */
    private List<MonthlyTrendVO> trends;

    /** 仓库库存价值分布 */
    private List<WarehouseValueVO> warehouseValues;

    /** 当月销售 TOP5 物料 */
    private List<TopMaterialVO> topMaterials;

    /** 月度走势行 */
    @Data
    public static class MonthlyTrendVO {
        /** 月份，格式 yyyy-MM */
        private String month;
        /** 当月销售额（元） */
        private BigDecimal salesAmount;
        /** 当月采购额（元） */
        private BigDecimal purchaseAmount;
    }

    /** 仓库库存价值分布行 */
    @Data
    public static class WarehouseValueVO {
        /** 仓库 ID */
        private Long warehouseId;
        /** 仓库名称 */
        private String warehouseName;
        /** 该仓库库存价值（qty × 加权价，元） */
        private BigDecimal value;
    }

    /** 当月销售 TOP 物料行 */
    @Data
    public static class TopMaterialVO {
        /** 物料 ID */
        private Long materialId;
        /** 物料编码 */
        private String materialCode;
        /** 物料名称 */
        private String materialName;
        /** 销售额（元） */
        private BigDecimal amount;
        /** 销售数量 */
        private BigDecimal qty;
    }
}
