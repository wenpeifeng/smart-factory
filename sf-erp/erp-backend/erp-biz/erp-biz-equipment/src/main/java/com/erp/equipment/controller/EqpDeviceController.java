package com.erp.equipment.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.equipment.dto.form.DeviceForm;
import com.erp.equipment.dto.form.DeviceStatusForm;
import com.erp.equipment.dto.request.DeviceQuery;
import com.erp.equipment.dto.vo.DeviceVO;
import com.erp.equipment.service.EqpDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 设备台账接口（CRUD + 状态流转）。
 * <p>
 * 状态流转规则：运行/停机/维修中三态互通，报废单向终态（详见 Service Javadoc 矩阵）。
 */
@Tag(name = "设备管理-设备台账")
@RestController
@RequestMapping("/equipment/devices")
@RequiredArgsConstructor
public class EqpDeviceController {

    private final EqpDeviceService deviceService;

    /** 台账分页（状态 + 编号/名称/型号关键词） */
    @Operation(summary = "设备台账分页查询")
    @RequirePerm("equipment:device:list")
    @GetMapping
    public Result<PageResult<DeviceVO>> page(@ModelAttribute DeviceQuery query) {
        return Result.ok(deviceService.page(query));
    }

    /** 设备详情 */
    @Operation(summary = "设备详情")
    @RequirePerm("equipment:device:list")
    @GetMapping("/{id}")
    public Result<DeviceVO> detail(@PathVariable Long id) {
        return Result.ok(deviceService.detail(id));
    }

    /** 新增设备（编号留空自动生成 SB+日期+流水） */
    @Operation(summary = "新增设备")
    @RequirePerm("equipment:device:add")
    @OperLog(module = "设备管理", operation = "新增设备")
    @Idempotent
    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody DeviceForm form) {
        return Result.ok(Map.of("id", deviceService.create(form)));
    }

    /** 修改设备（编号与状态不走本接口） */
    @Operation(summary = "修改设备")
    @RequirePerm("equipment:device:edit")
    @OperLog(module = "设备管理", operation = "修改设备")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DeviceForm form) {
        deviceService.update(id, form);
        return Result.ok();
    }

    /** 删除设备（逻辑删除，存在未完结工单时拒绝） */
    @Operation(summary = "删除设备")
    @RequirePerm("equipment:device:delete")
    @OperLog(module = "设备管理", operation = "删除设备")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return Result.ok();
    }

    /** 状态流转（报废单向终态） */
    @Operation(summary = "设备状态流转")
    @RequirePerm("equipment:device:edit")
    @OperLog(module = "设备管理", operation = "启停设备")
    @Idempotent
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody DeviceStatusForm form) {
        deviceService.changeStatus(id, form);
        return Result.ok();
    }
}
