package com.erp.equipment.controller;

import com.erp.common.result.Result;
import com.erp.equipment.dto.vo.StatisticsVO;
import com.erp.equipment.service.EqpStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.security.RequirePerm;

/**
 * 设备管理统计接口（状态分布 / 当月维修 TOP5 / 当月备件成本）。
 */
@Tag(name = "设备管理-统计")
@RestController
@RequestMapping("/equipment/statistics")
@RequiredArgsConstructor
public class EqpStatisticsController {

    private final EqpStatisticsService statisticsService;

    /** 汇总统计 */
    @Operation(summary = "设备管理统计汇总")
    @RequirePerm("equipment:device:list")
    @GetMapping
    public Result<StatisticsVO> statistics() {
        return Result.ok(statisticsService.statistics());
    }
}
