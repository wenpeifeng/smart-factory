package com.erp.equipment.service.impl;

import com.erp.equipment.dto.vo.StatisticsVO;
import com.erp.equipment.mapper.EqpDeviceMapper;
import com.erp.equipment.mapper.EqpRepairMapper;
import com.erp.equipment.service.EqpStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备管理统计服务实现。
 * <p>
 * 月度口径统一为「当月」（自然月，服务器时区）：维修次数按工单报修时间归月，
 * 备件消耗成本按完工工单（finish_time）的明细 cost_ref 合计。
 */
@Service
@RequiredArgsConstructor
public class EqpStatisticsServiceImpl implements EqpStatisticsService {

    private final EqpDeviceMapper deviceMapper;
    private final EqpRepairMapper repairMapper;

    @Override
    public StatisticsVO statistics() {
        StatisticsVO vo = new StatisticsVO();
        vo.setStatusDist(buildStatusDist());
        vo.setMonthRepairTop(deviceMapper.selectMonthRepairTop(monthStart()));
        BigDecimal cost = repairMapper.sumMonthSpareCost(monthStart());
        vo.setMonthSpareCost(cost == null ? BigDecimal.ZERO : cost);
        return vo;
    }

    /** 状态分布：四种状态全量返回，无设备的状态补 0，前端饼图不出空扇区 */
    private List<StatisticsVO.StatusCount> buildStatusDist() {
        Map<Integer, Long> byStatus = deviceMapper.countByStatus().stream()
                .collect(Collectors.toMap(StatisticsVO.StatusCount::getStatus,
                        StatisticsVO.StatusCount::getCount));
        List<StatisticsVO.StatusCount> result = new ArrayList<>(4);
        for (int status = 1; status <= 4; status++) {
            long count = byStatus.getOrDefault(status, 0L);
            result.add(new StatisticsVO.StatusCount(status,
                    com.erp.equipment.dto.vo.DeviceVO.statusLabelOf(status), count));
        }
        return result;
    }

    /** 当月第一天（含） */
    private LocalDate monthStart() {
        return YearMonth.now().atDay(1);
    }
}
