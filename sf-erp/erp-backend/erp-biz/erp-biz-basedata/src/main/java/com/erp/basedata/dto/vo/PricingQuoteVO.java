package com.erp.basedata.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 定价引擎报价 VO（GET /basedata/pricing/quote 的响应）。
 * <p>
 * 优先级：特价审批 &gt; 等级价 &gt; 批量价 &gt; 参考成本×加成系数；
 * hitStrategy 标识命中的策略，references 返回全部可用参照价供前端展示。
 */
@Data
public class PricingQuoteVO {

    /** 命中策略：特价 */
    public static final String HIT_SPECIAL = "SPECIAL";
    /** 命中策略：等级价 */
    public static final String HIT_LEVEL = "LEVEL";
    /** 命中策略：批量价 */
    public static final String HIT_QTY = "QTY";
    /** 命中策略：参考成本加成 */
    public static final String HIT_MARKUP = "MARKUP";
    /** 未命中：无任何可用价格（未维护策略且物料参考成本为空） */
    public static final String HIT_NONE = "NONE";

    /** 客户 ID */
    private Long customerId;

    /** 客户等级（参与等级价匹配） */
    private String customerLevel;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码（冗余） */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 数量 */
    private BigDecimal qty;

    /** 建议单价（HALF_UP 2 位；HIT_NONE 时为 null） */
    private BigDecimal unitPrice;

    /** 命中策略：SPECIAL/LEVEL/QTY/MARKUP/NONE */
    private String hitStrategy;

    /** 命中策略中文 */
    private String hitStrategyLabel;

    /** 命中说明（如"特价(≤2026-12-31)"、"批量价(≥100)"） */
    private String hitReason;

    /** 参照价列表（全部候选价，含未命中项，前端并列展示） */
    private List<RefItem> references = new ArrayList<>();

    /** 参照价条目 */
    @Data
    public static class RefItem {

        /** 策略键：SPECIAL/LEVEL/QTY/MARKUP */
        private String strategy;

        /** 策略中文 */
        private String label;

        /** 单价（原始精度，展示用） */
        private BigDecimal price;

        /** 是否命中 */
        private boolean hit;

        /** 构造 */
        public RefItem() {
        }

        /**
         * 构造参照条目。
         *
         * @param strategy 策略键
         * @param label    策略中文
         * @param price    单价
         * @param hit      是否命中
         */
        public RefItem(String strategy, String label, BigDecimal price, boolean hit) {
            this.strategy = strategy;
            this.label = label;
            this.price = price;
            this.hit = hit;
        }
    }

    /** 命中策略中文标签 */
    public static String labelOf(String strategy) {
        if (strategy == null) {
            return "";
        }
        return switch (strategy) {
            case HIT_SPECIAL -> "特价";
            case HIT_LEVEL -> "等级价";
            case HIT_QTY -> "批量价";
            case HIT_MARKUP -> "成本加成";
            case HIT_NONE -> "无可用价";
            default -> strategy;
        };
    }
}
