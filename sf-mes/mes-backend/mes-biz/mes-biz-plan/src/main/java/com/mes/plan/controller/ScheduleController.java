package com.mes.plan.controller;

import com.mes.common.result.Result;
import com.mes.framework.security.RequirePerm;
import com.mes.plan.dto.vo.ScheduleVO;
import com.mes.plan.service.ScheduleService;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工序排程接口（工作中心甘特时间片；甘特页面随 P3 呈现层建设，先开放数据接口）。
 */
@Tag(name = "计划-工序排程")
@RestController
@RequestMapping("/plan/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /**
     * 保存工序排程时间片。
     */
    @Operation(summary = "保存排程时间片")
    @RequirePerm("plan:schedule:edit")
    @OperLog(module = "工序排程", operation = "保存排程时间片")
    @PostMapping
    public Result<Void> save(@RequestParam Long woStepId,
                             @RequestParam Long workcenterId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                             @RequestParam(required = false) Integer lockFlag) {
        scheduleService.save(woStepId, workcenterId, start, end, lockFlag);
        return Result.ok();
    }

    /**
     * 工作中心时间片列表（甘特）。
     */
    @Operation(summary = "工作中心排程列表")
    @RequirePerm("plan:schedule:list")
    @GetMapping
    public Result<List<ScheduleVO>> list(@RequestParam Long workcenterId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.ok(scheduleService.listByWorkcenter(workcenterId, from, to));
    }
}
