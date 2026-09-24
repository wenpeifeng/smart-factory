package com.mes.equipment.service.impl;

import com.mes.basedata.entity.BaseWorkcenter;
import com.mes.basedata.mapper.BaseWorkcenterMapper;
import com.mes.equipment.dto.vo.OeeVO;
import com.mes.equipment.mapper.OeeStatsMapper;
import com.mes.equipment.service.OeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OEE 计算实现。
 * <p>P3 简化口径：
 * <ul>
 *   <li>负荷时间 = 区间天数 x 工作中心日产能工时（无排班加载，天数按区间自然日）；</li>
 *   <li>运转时间 = 过站工时合计；</li>
 *   <li>性能开动率 = 理论工时(合格数 x 单件工时) / 运转时间，>1 截断为 1；</li>
 *   <li>合格品率 = 合格 / (合格 + 报废)。</li>
 * </ul>接入排班日历后负荷时间可精确到班次。
 */
@Service
@RequiredArgsConstructor
public class OeeServiceImpl implements OeeService {

    private final OeeStatsMapper statsMapper;
    private final BaseWorkcenterMapper workcenterMapper;

    @Override
    public List<OeeVO> calc(Long workcenterId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime effectiveFrom = from != null ? from : LocalDateTime.now().minusDays(30);
        LocalDateTime effectiveTo = to != null ? to : LocalDateTime.now();
        List<Map<String, Object>> rows = statsMapper.aggregate(workcenterId, effectiveFrom, effectiveTo);
        long days = Math.max(1, Duration.between(effectiveFrom, effectiveTo).toDays());

        List<OeeVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long wcId = row.get("workcenterId") == null ? null : ((Number) row.get("workcenterId")).longValue();
            BigDecimal run = decimal(row.get("runMinutes"));
            BigDecimal good = decimal(row.get("goodQty"));
            BigDecimal scrap = decimal(row.get("scrapQty"));
            BigDecimal theory = decimal(row.get("theoryMinutes"));

            OeeVO vo = new OeeVO();
            vo.setWorkcenterId(wcId);
            if (wcId != null) {
                BaseWorkcenter wc = workcenterMapper.selectById(wcId);
                vo.setWorkcenterName(wc != null ? wc.getName() : null);
                BigDecimal planned = wc != null && wc.getCapacityHoursPerDay() != null
                        ? wc.getCapacityHoursPerDay().multiply(BigDecimal.valueOf(days))
                                .multiply(BigDecimal.valueOf(60))
                        : BigDecimal.ZERO;
                vo.setPlannedMinutes(planned);
                vo.setAvailability(planned.compareTo(BigDecimal.ZERO) > 0
                        ? ratio(run.divide(planned, 4, RoundingMode.HALF_UP)) : BigDecimal.ZERO);
            } else {
                vo.setPlannedMinutes(BigDecimal.ZERO);
                vo.setAvailability(BigDecimal.ZERO);
            }
            vo.setRunMinutes(run);
            vo.setPerformance(run.compareTo(BigDecimal.ZERO) > 0
                    ? ratio(theory.divide(run, 4, RoundingMode.HALF_UP)) : BigDecimal.ZERO);
            BigDecimal produced = good.add(scrap);
            vo.setQuality(produced.compareTo(BigDecimal.ZERO) > 0
                    ? good.divide(produced, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            vo.setOee(vo.getAvailability().multiply(vo.getPerformance()).multiply(vo.getQuality())
                    .setScale(4, RoundingMode.HALF_UP));
            result.add(vo);
        }
        return result;
    }

    private BigDecimal ratio(BigDecimal v) {
        return v.min(BigDecimal.ONE).max(BigDecimal.ZERO);
    }

    private BigDecimal decimal(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }
}
