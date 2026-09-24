package com.mes.report.service.impl;

import com.mes.report.dto.vo.CockpitVO;
import com.mes.report.dto.vo.ScreenVO;
import com.mes.report.mapper.StatsMapper;
import com.mes.report.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统计服务实现（直接透出聚合 Mapper 结果，口径见 StatsMapper 注释）。
 */
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final StatsMapper statsMapper;

    @Override
    public ScreenVO screen() {
        ScreenVO vo = new ScreenVO();
        List<Map<String, Object>> andons = statsMapper.openAndons();
        vo.setAndons(andons);
        vo.setOpenAndonCount(andons.stream()
                .mapToInt(a -> ((Number) a.get("cnt")).intValue()).sum());
        vo.setWoStatus(statsMapper.woStatusDist());
        vo.setWipTop(statsMapper.wipByOperation());
        vo.setWoProgress(statsMapper.woProgress());
        vo.setDailyOutput(statsMapper.dailyOutput(LocalDateTime.now().minusDays(7)));
        vo.setQuality(statsMapper.qualitySummary());
        return vo;
    }

    @Override
    public CockpitVO cockpit() {
        CockpitVO vo = new CockpitVO();
        vo.setWoStatus(statsMapper.woStatusDist());
        vo.setDailyOutput(statsMapper.dailyOutput(LocalDateTime.now().minusDays(14)));
        vo.setQuality(statsMapper.qualitySummary());
        Integer shortCnt = statsMapper.shortWorkorders();
        vo.setShortWorkorders(shortCnt == null ? 0 : shortCnt);
        Integer pushCnt = statsMapper.pendingPushes();
        vo.setPendingPushes(pushCnt == null ? 0 : pushCnt);
        List<Map<String, Object>> andons = statsMapper.openAndons();
        vo.setOpenAndons(andons.stream()
                .mapToInt(a -> ((Number) a.get("cnt")).intValue()).sum());
        return vo;
    }

    @Override
    public List<Map<String, Object>> workorderAchievement(LocalDate from, LocalDate to) {
        return statsMapper.workorderAchievement(from, to);
    }

    @Override
    public List<Map<String, Object>> stationDetail(int days) {
        return statsMapper.stationDetail(LocalDateTime.now().minusDays(Math.max(1, days)));
    }

    @Override
    public List<Map<String, Object>> qualityByMaterial() {
        return statsMapper.qualityByMaterial();
    }
}
