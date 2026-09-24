package com.mes.equipment.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.equipment.dto.form.MaintainForm;
import com.mes.equipment.entity.EqpMaintain;
import com.mes.equipment.service.MaintainService;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备保养/维修接口。
 */
@Tag(name = "设备-保养维修")
@RestController
@RequestMapping("/equipment/maintains")
@RequiredArgsConstructor
public class MaintainController {

    private final MaintainService maintainService;

    /**
     * 创建保养/维修单。
     */
    @Operation(summary = "创建保养/维修单")
    @RequirePerm("equipment:maintain:add")
    @OperLog(module = "保养维修", operation = "创建保养/维修单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody MaintainForm form) {
        return Result.ok(maintainService.create(form));
    }

    /**
     * 完成保养/维修（设备恢复闲置）。
     */
    @Operation(summary = "完成保养/维修")
    @RequirePerm("equipment:maintain:finish")
    @OperLog(module = "保养维修", operation = "完成保养/维修")
    @Idempotent
    @PutMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id,
                               @RequestParam(required = false) String maintainer,
                               @RequestParam(required = false) Integer durationMinutes) {
        maintainService.finish(id, maintainer, durationMinutes);
        return Result.ok();
    }

    /**
     * 分页查询。
     */
    @Operation(summary = "分页查询保养/维修")
    @RequirePerm("equipment:maintain:list")
    @GetMapping
    public Result<PageResult<EqpMaintain>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "10") long pageSize,
                                                @RequestParam(required = false) Long deviceId,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(required = false) String status) {
        return Result.ok(maintainService.page(pageNum, pageSize, deviceId, type, status));
    }
}
