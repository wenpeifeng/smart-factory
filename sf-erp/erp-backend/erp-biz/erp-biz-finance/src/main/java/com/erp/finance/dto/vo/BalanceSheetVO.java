package com.erp.finance.dto.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 资产负债表（period=yyyy-MM，期末科目余额取数，映射驱动）。
 * <p>
 * 勾稽断言：资产总计 = 负债合计 + 所有者权益合计（tolerance 0.01，
 * 不平返回 balanced=false 与差额）；期初列同理校验。
 *
 * @param period              会计期间（yyyy-MM）
 * @param bookId              账套 ID
 * @param rows                行项目（按 side 分组前的全量行，含计算行）
 * @param assetTotal          资产总计（期末）
 * @param liabTotal           负债合计（期末）
 * @param equityTotal         所有者权益合计（期末）
 * @param liabEquityTotal     负债和所有者权益总计（期末）
 * @param balanced            期初期末两侧合计均平衡
 * @param diff                期末差额（资产总计 − 负债和所有者权益总计）
 * @param openingDiff         期初差额
 * @param profitLossNotCarried 损益类科目存在未结转余额（已按现行余额并入未分配利润列示）
 * @param warnings            口径提示（未映射覆盖科目 / 映射重叠等，空列表 = 无）
 */
public record BalanceSheetVO(String period, Long bookId, List<Row> rows,
                             BigDecimal assetTotal, BigDecimal liabTotal,
                             BigDecimal equityTotal, BigDecimal liabEquityTotal,
                             boolean balanced, BigDecimal diff, BigDecimal openingDiff,
                             boolean profitLossNotCarried, List<String> warnings) {

    /**
     * 资产负债表行。
     *
     * @param itemName  行项目名称（含「减：」等前缀）
     * @param side      ASSET / LIABILITY / EQUITY（计算行按种子挂靠侧）
     * @param direction 取数方向 DEBIT(借-贷) / CREDIT(贷-借)
     * @param calcType  计算行类型（空=取数行）
     * @param total     是否合计/计算行（前端加粗）
     * @param subjectCodes 取数科目编码（展示用）
     * @param opening   期初余额
     * @param closing   期末余额
     */
    public record Row(String itemName, String side, String direction, String calcType,
                      boolean total, String subjectCodes,
                      BigDecimal opening, BigDecimal closing) {
    }
}
