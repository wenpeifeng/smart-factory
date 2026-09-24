package com.erp.sales.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.sales.dto.vo.SalDailyStatVO;
import com.erp.sales.dto.vo.SalRankVO;
import com.erp.sales.mapper.SalesBizMapper;
import com.erp.sales.service.SalStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 销售统计 Service 实现（只读查询，无事务需求）。
 */
@Service
@RequiredArgsConstructor
public class SalStatisticsServiceImpl implements SalStatisticsService {

    private final SalesBizMapper bizMapper;

    @Override
    public List<SalDailyStatVO> dailyStats(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw BusinessException.ofKey("erp.sal.stat-dates-required");
        }
        if (from.isAfter(to)) {
            throw BusinessException.ofKey("erp.sal.date-order");
        }
        return bizMapper.selectDailyStats(from, to);
    }

    @Override
    public List<SalRankVO> rank(String type, LocalDate from, LocalDate to) {
        if ("material".equals(type)) {
            return bizMapper.selectMaterialRank(from, to);
        }
        if ("customer".equals(type)) {
            return bizMapper.selectCustomerRank(from, to);
        }
        throw BusinessException.ofKey("erp.sal.rank-type-unsupported", type);
    }
}
