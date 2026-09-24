package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 报价单行毛利率视图行。
 * <p>
 * 毛利率口径：(报价单价 − 成本参考) / 报价单价 × 100（按报价原价口径，
 * 与"下推订单单价 = 报价 ×(1−折扣)"独立）；低于警戒线
 * {@link com.erp.sales.constant.SalesLinkConstants#MARGIN_WARN_THRESHOLD}
 * 时 warn=true，前端红色标记。
 */
@Data
public class QuotationMarginRowVO {

    /** 明细行 ID */
    private Long itemId;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 规格型号 */
    private String materialSpec;

    /** 报价数量 */
    private BigDecimal qty;

    /** 报价单价 */
    private BigDecimal price;

    /** 成本参考（快照） */
    private BigDecimal costPrice;

    /** 行毛利额 = (报价单价 − 成本) × 数量，四舍五入 2 位 */
    private BigDecimal marginAmount;

    /** 行毛利率%（= (报价 − 成本)/报价 × 100，保留 2 位；报价为 0 时为 null） */
    private BigDecimal marginRate;

    /** 是否低于警戒线（毛利率 < 20%） */
    private boolean warn;

    /** 行备注 */
    private String remark;
}
