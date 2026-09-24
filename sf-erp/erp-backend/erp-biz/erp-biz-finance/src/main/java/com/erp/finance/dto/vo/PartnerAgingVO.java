package com.erp.finance.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 应收/应付账龄分析（客户×账龄分桶 / 供应商×账龄分桶）。
 * <p>
 * 口径：未结算余额 = amount − settled_amount（&gt;0 才参与）；
 * 账龄锚点 = COALESCE(biz_date, DATE(create_time))（单据日期，缺失回退创建日）；
 * asOf 时点之后立据的单据不计入（未来单据不属于该时点存量）。
 * 分桶：0-30 / 31-60 / 61-90 / 91-180 / 181+ 天（首桶含当日，标签「1-30天」）。
 *
 * @param side      RECEIVABLE 应收（客户）/ PAYABLE 应付（供应商）
 * @param asOf      账龄计算时点（yyyy-MM-dd，默认今天）
 * @param bookId    账套 ID
 * @param buckets   分桶汇总（金额 + 占比）
 * @param rows      客商行（按未结算余额降序）
 * @param totalRow  合计行
 * @param totalAmount 未结算余额总计
 * @param totalDocCount 单据数总计
 */
public record PartnerAgingVO(String side, LocalDate asOf, Long bookId,
                             List<Bucket> buckets, List<Row> rows, Row totalRow,
                             BigDecimal totalAmount, long totalDocCount) {

    /** 分桶键：D30 / D60 / D90 / D180 / D180P */
    public record Bucket(String key, String label, BigDecimal amount, BigDecimal ratio) {
    }

    /**
     * 账龄行（客商粒度）。
     *
     * @param partnerId   客商 ID
     * @param partnerName 客商名称（base 表未落库时为 null，前端显示 #id）
     * @param docCount    未结算单据数
     * @param d30         1-30 天金额
     * @param d60         31-60 天金额
     * @param d90         61-90 天金额
     * @param d180        91-180 天金额
     * @param d180p       180 天以上金额
     * @param total       未结算余额合计
     * @param ratio       占总额比（4 位小数）
     */
    public record Row(Long partnerId, String partnerName, long docCount,
                      BigDecimal d30, BigDecimal d60, BigDecimal d90,
                      BigDecimal d180, BigDecimal d180p,
                      BigDecimal total, BigDecimal ratio) {
    }
}
