package com.erp.invoice.service;

import com.erp.invoice.dto.vo.MonthlyStatVO;
import com.erp.invoice.dto.vo.ReconciliationVO;
import com.erp.invoice.dto.vo.TaxStatVO;

import java.time.LocalDate;

/**
 * 税务统计与对账辅助服务。
 */
public interface InvStatisticsService {

    /**
     * 期间税负统计：销项税额（按开票日期）/ 进项税额（已认证，按认证日期）/
     * 应纳增值税 = 销项 − 进项 / 税负率 = 应纳 ÷ 不含税销售额 × 100。
     *
     * @param from 期间起（含，缺省当天前推一年）
     * @param to   期间止（含，缺省当天）
     * @return 税负统计 VO
     */
    TaxStatVO tax(LocalDate from, LocalDate to);

    /**
     * 年度 12 个月价税走势（销项/进项的价税合计与税额，无发生额月份补 0）。
     *
     * @param year 年度（缺省当前年）
     * @return 12 行月度数据
     */
    MonthlyStatVO monthly(Integer year);

    /**
     * 客户/供应商对账辅助：期间业务金额 vs 开票金额 vs 未开票差额（不含税口径）。
     *
     * @param partnerType 往来类型：customer / supplier
     * @param partnerId   往来单位 ID
     * @param from        期间起（含，缺省当天前推一年）
     * @param to          期间止（含，缺省当天）
     * @return 对账 VO
     * @throws com.erp.common.exception.BusinessException partnerType 非法
     */
    ReconciliationVO reconciliation(String partnerType, Long partnerId, LocalDate from, LocalDate to);
}
