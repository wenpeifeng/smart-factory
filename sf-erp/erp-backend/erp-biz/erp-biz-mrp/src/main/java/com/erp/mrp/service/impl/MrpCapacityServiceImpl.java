package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.mrp.constant.MrpShopConstants;
import com.erp.mrp.dto.vo.CapacityLoadResultVO;
import com.erp.mrp.dto.vo.CapacityLoadVO;
import com.erp.mrp.dto.vo.WorkCenterLoadVO;
import com.erp.mrp.entity.MrpWorkCenter;
import com.erp.mrp.entity.MrpWoStep;
import com.erp.mrp.mapper.MrpWorkCenterMapper;
import com.erp.mrp.mapper.MrpWoStepMapper;
import com.erp.mrp.service.MrpCapacityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力计划(CRP 简版)服务实现。
 * <p>
 * 口径（简版）：
 * <ul>
 *   <li>负荷工时 = 未完工(status!=FINISHED) mrp_wo_step 的 计划数量×单件工时 汇总 ÷ 60，按工作中心分组；</li>
 *   <li>可用工时 = 每日产能工时 × 期间工作日数（周六周日不计）；</li>
 *   <li>负荷率 = 负荷 ÷ 可用 × 100（1位小数），>100 为超载。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MrpCapacityServiceImpl implements MrpCapacityService {

    private final MrpWorkCenterMapper workCenterMapper;
    private final MrpWoStepMapper woStepMapper;

    @Override
    public CapacityLoadResultVO load(LocalDate from, LocalDate to, Long workCenterId) {
        LocalDate fromDate = from != null ? from : LocalDate.now();
        LocalDate toDate = to != null ? to : fromDate.plusDays(MrpShopConstants.CRP_DEFAULT_DAYS - 1L);
        if (toDate.isBefore(fromDate)) {
            throw BusinessException.ofKey("erp.mrp.date-order");
        }
        long workdays = countWorkdays(fromDate, toDate);

        List<MrpWorkCenter> centers = workCenterMapper.selectList(
                Wrappers.lambdaQuery(MrpWorkCenter.class)
                        .eq(workCenterId != null, MrpWorkCenter::getId, workCenterId)
                        .orderByAsc(MrpWorkCenter::getCode));
        Map<Long, BigDecimal> loadMinutes = loadMinutesMap();
        Map<Long, Long> pendingCounts = pendingCountsMap(workCenterId);

        CapacityLoadResultVO result = new CapacityLoadResultVO();
        result.setFromDate(fromDate);
        result.setToDate(toDate);
        result.setWorkdays(workdays);
        for (MrpWorkCenter wc : centers) {
            CapacityLoadVO row = new CapacityLoadVO();
            row.setWorkCenterId(wc.getId());
            row.setCode(wc.getCode());
            row.setName(wc.getName());
            row.setWorkshop(wc.getWorkshop());
            row.setStatus(wc.getStatus());
            BigDecimal minutes = loadMinutes.getOrDefault(wc.getId(), BigDecimal.ZERO);
            BigDecimal loadHours = minutes.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            BigDecimal availableHours = wc.getCapacityHoursPerDay()
                    .multiply(BigDecimal.valueOf(workdays)).setScale(2, RoundingMode.HALF_UP);
            row.setLoadHours(loadHours);
            row.setAvailableHours(availableHours);
            row.setPendingStepCount(pendingCounts.getOrDefault(wc.getId(), 0L));
            if (availableHours.signum() > 0) {
                BigDecimal rate = loadHours.multiply(BigDecimal.valueOf(100))
                        .divide(availableHours, 1, RoundingMode.HALF_UP);
                row.setLoadRate(rate);
                row.setOverloaded(rate.compareTo(BigDecimal.valueOf(100)) > 0);
            } else {
                // 可用工时为0但有负荷 → 视为超载（rate 置空由前端特殊展示）
                row.setLoadRate(null);
                row.setOverloaded(loadHours.signum() > 0);
            }
            result.getRows().add(row);
            if (Boolean.TRUE.equals(row.getOverloaded())) {
                result.getOverloads().add(row);
            }
        }
        return result;
    }

    // ==================== 私有工具 ====================

    /** 期间工作日数（周一~周五） */
    private long countWorkdays(LocalDate from, LocalDate to) {
        long days = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            switch (d.getDayOfWeek()) {
                case SATURDAY, SUNDAY -> { /* 周末不计 */ }
                default -> days++;
            }
        }
        return days;
    }

    /** 工作中心 → 未完工任务负荷分钟数 */
    private Map<Long, BigDecimal> loadMinutesMap() {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (WorkCenterLoadVO row : woStepMapper.sumLoadMinutesByWorkCenter()) {
            map.put(row.getWorkCenterId(),
                    row.getLoadMinutes() == null ? BigDecimal.ZERO : row.getLoadMinutes());
        }
        return map;
    }

    /** 工作中心 → 未完工任务数（看板辅助信息） */
    private Map<Long, Long> pendingCountsMap(Long workCenterId) {
        List<MrpWoStep> pending = woStepMapper.selectList(Wrappers.lambdaQuery(MrpWoStep.class)
                .select(MrpWoStep::getWorkCenterId)
                .eq(workCenterId != null, MrpWoStep::getWorkCenterId, workCenterId)
                .ne(MrpWoStep::getStatus, MrpShopConstants.STEP_FINISHED));
        Map<Long, Long> counts = new HashMap<>();
        for (MrpWoStep step : pending) {
            if (step.getWorkCenterId() != null) {
                counts.merge(step.getWorkCenterId(), 1L, Long::sum);
            }
        }
        return counts;
    }
}
