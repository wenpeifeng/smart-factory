package com.mes.equipment.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.equipment.dto.form.CheckForm;
import com.mes.equipment.entity.EqpCheck;
import com.mes.equipment.service.CheckService;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备点检接口。
 */
@Tag(name = "设备-点检")
@RestController
@RequestMapping("/equipment/checks")
@RequiredArgsConstructor
public class CheckController {

    private final CheckService checkService;

    /**
     * 登记点检（NG 自动置设备维修态）。
     */
    @Operation(summary = "登记点检")
    @RequirePerm("equipment:check:add")
    @OperLog(module = "设备点检", operation = "登记点检")
    @Idempotent
    @PostMapping
    public Result<Long> record(@Valid @RequestBody CheckForm form) {
        return Result.ok(checkService.record(form));
    }

    /**
     * 分页查询点检记录。
     */
    @Operation(summary = "分页查询点检记录")
    @RequirePerm("equipment:check:list")
    @GetMapping
    public Result<PageResult<EqpCheck>> page(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             @RequestParam(required = false) Long deviceId) {
        return Result.ok(checkService.page(pageNum, pageSize, deviceId));
    }
}
