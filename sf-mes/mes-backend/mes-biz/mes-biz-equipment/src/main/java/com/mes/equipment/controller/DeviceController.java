package com.mes.equipment.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.equipment.dto.form.DeviceForm;
import com.mes.equipment.dto.vo.DeviceVO;
import com.mes.equipment.service.DeviceService;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备台账接口。
 */
@Tag(name = "设备-台账")
@RestController
@RequestMapping("/equipment/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * 分页查询设备。
     */
    @Operation(summary = "分页查询设备")
    @RequirePerm("equipment:device:list")
    @GetMapping
    public Result<PageResult<DeviceVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long workcenterId,
                                             @RequestParam(required = false) String status) {
        return Result.ok(deviceService.page(pageNum, pageSize, keyword, workcenterId, status));
    }

    /**
     * 新增设备。
     */
    @Operation(summary = "新增设备")
    @RequirePerm("equipment:device:add")
    @OperLog(module = "设备台账", operation = "新增设备")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody DeviceForm form) {
        return Result.ok(deviceService.create(form));
    }

    /**
     * 修改设备。
     */
    @Operation(summary = "修改设备")
    @RequirePerm("equipment:device:edit")
    @OperLog(module = "设备台账", operation = "修改设备")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DeviceForm form) {
        deviceService.update(id, form);
        return Result.ok();
    }

    /**
     * 删除设备。
     */
    @Operation(summary = "删除设备")
    @RequirePerm("equipment:device:delete")
    @OperLog(module = "设备台账", operation = "删除设备")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return Result.ok();
    }

    /**
     * 变更设备状态。
     */
    @Operation(summary = "变更设备状态")
    @RequirePerm("equipment:device:edit")
    @OperLog(module = "设备台账", operation = "变更设备状态")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam String status) {
        deviceService.changeStatus(id, status);
        return Result.ok();
    }
}
