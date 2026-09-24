package com.erp.cost.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.cost.dto.raw.MonthlyTotalRaw;
import com.erp.cost.dto.vo.SettleSummaryVO;
import com.erp.cost.dto.vo.TrendPointVO;
import com.erp.cost.mapper.CostFlowMapper;
import com.erp.cost.service.CostAnalysisService;
import com.erp.cost.service.CostSettleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 成本分析服务实现。
 * <p>
 * 趋势口径：期末存货余额(P) = 期初余额(from 前) + Σ(in−out, from..P)，
 * 与台账逐行运行余额推算等价（金额收支守恒），避免逐月每物料取最后行的重查询。
 */
@Service
@RequiredArgsConstructor
public class CostAnalysisServiceImpl implements CostAnalysisService {

    private final CostFlowMapper flowMapper;
    private final CostSettleService settleService;

    @Override
    public SettleSummaryVO materialSummary(String period) {
        return settleService.summary(period);
    }

    @Override
    public List<TrendPointVO> trend(String from, String to) {
        String fromP = requirePeriod(from, "from");
        String toP = requirePeriod(to, "to");
        if (fromP.compareTo(toP) > 0) {
            throw BusinessException.ofKey("erp.cost.trend-period-order", fromP, toP);
        }
        // 期间序列（含首尾，最多 120 期防御）
        List<String> periods = new ArrayList<>();
        YearMonth cursor = YearMonth.parse(fromP, com.erp.cost.constant.CostConstants.PERIOD_FORMATTER);
        YearMonth end = YearMonth.parse(toP, com.erp.cost.constant.CostConstants.PERIOD_FORMATTER);
        while (!cursor.isAfter(end) && periods.size() < 120) {
            periods.add(cursor.toString().replace("-", ""));
            cursor = cursor.plusMonths(1);
        }

        BigDecimal opening = flowMapper.selectOpeningBalance(fromP);
        Map<String, MonthlyTotalRaw> totals = flowMapper.selectMonthlyTotals(fromP, toP).stream()
                .collect(Collectors.toMap(MonthlyTotalRaw::getPeriod, t -> t));

        List<TrendPointVO> points = new ArrayList<>(periods.size());
        BigDecimal balance = opening == null ? BigDecimal.ZERO : opening;
        for (String p : periods) {
            MonthlyTotalRaw t = totals.get(p);
            BigDecimal in = t == null || t.getInAmount() == null ? BigDecimal.ZERO : t.getInAmount();
            BigDecimal out = t == null || t.getOutAmount() == null ? BigDecimal.ZERO : t.getOutAmount();
            BigDecimal salOut = t == null || t.getSalOutCost() == null ? BigDecimal.ZERO : t.getSalOutCost();
            BigDecimal prodUse = t == null || t.getProdUseCost() == null ? BigDecimal.ZERO : t.getProdUseCost();
            balance = balance.add(in).subtract(out);
            points.add(new TrendPointVO(p, in, out, salOut, prodUse, balance));
        }
        return points;
    }

    private String requirePeriod(String period, String field) {
        if (period == null || period.isBlank()) {
            throw BusinessException.ofKey("erp.cost.trend-param-required", field);
        }
        try {
            YearMonth.parse(period.trim(), com.erp.cost.constant.CostConstants.PERIOD_FORMATTER);
        } catch (DateTimeParseException e) {
            throw BusinessException.ofKey("erp.cost.trend-param-invalid", field, period);
        }
        return period.trim();
    }
}
