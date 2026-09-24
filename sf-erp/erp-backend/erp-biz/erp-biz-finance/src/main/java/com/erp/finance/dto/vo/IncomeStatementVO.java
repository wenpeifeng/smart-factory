package com.erp.finance.dto.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 利润表（本月数 + 本年累计数，映射驱动）。
 * <p>
 * 默认 period=yyyy-MM（本月=该自然月，本年累计=该月所在年 1 月至月末）；
 * 显式 from/to 时本月列切换为 [from,to] 自定义区间，本年累计=from 所在年年初至 to。
 *
 * @param period    会计期间（yyyy-MM）
 * @param from      本月（本期）区间起（含）
 * @param to        本月（本期）区间止（含）
 * @param ytdFrom   本年累计区间起（含）
 * @param ytdTo     本年累计区间止（含）
 * @param bookId    账套 ID
 * @param rows      行项目（含营业利润/利润总额/净利润计算行）
 * @param netProfitMonth 净利润（本月/本期）
 * @param netProfitYtd   净利润（本年累计）
 * @param warnings  口径提示（损益类科目未被映射覆盖等，空列表 = 无）
 */
public record IncomeStatementVO(String period, String from, String to,
                                String ytdFrom, String ytdTo, Long bookId,
                                List<Row> rows,
                                BigDecimal netProfitMonth, BigDecimal netProfitYtd,
                                List<String> warnings) {

    /**
     * 利润表行。
     *
     * @param itemName    行项目名称（含「一、」「减：」等前缀）
     * @param direction   取数方向 CREDIT 收入类(贷-借) / DEBIT 成本费用类(借-贷)
     * @param calcType    计算行类型（空=取数行）
     * @param total       是否小计/计算行（前端加粗）
     * @param subjectCodes 取数科目编码（展示用）
     * @param monthAmount 本月（本期）数
     * @param ytdAmount   本年累计数
     */
    public record Row(String itemName, String direction, String calcType, boolean total,
                      String subjectCodes, BigDecimal monthAmount, BigDecimal ytdAmount) {
    }
}
