package com.mes.equipment.controller;

import com.mes.common.result.Result;
import com.mes.equipment.dto.vo.OeeVO;
import com.mes.equipment.service.OeeService;
import com.mes.framework.security.RequirePerm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OEE 计算接口（工作中心维度实时聚合）。
 */
@Tag(name = "设备-OEE")
@RestController
@RequestMapping("/equipment/oee")
@RequiredArgsConstructor
public class OeeController {

    private final OeeService oeeService;

    /**
     * 计算工作中心 OEE。
     */
    @Operation(summary = "工作中心OEE")
    @RequirePerm("equipment:oee:query")
    @GetMapping
    public Result<List<OeeVO>> calc(@RequestParam(required = false) Long workcenterId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.ok(oeeService.calc(workcenterId, from, to));
    }
}
