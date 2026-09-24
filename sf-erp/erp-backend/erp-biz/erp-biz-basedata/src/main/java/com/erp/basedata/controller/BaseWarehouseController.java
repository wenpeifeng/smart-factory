package com.erp.basedata.controller;

import com.erp.basedata.dto.form.BaseLocationForm;
import com.erp.basedata.dto.form.BaseWarehouseForm;
import com.erp.basedata.dto.vo.BaseLocationVO;
import com.erp.basedata.dto.vo.BaseWarehouseVO;
import com.erp.basedata.service.BaseLocationService;
import com.erp.basedata.service.BaseWarehouseService;
import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
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

import java.util.List;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 仓库接口（含仓库下库位管理）。
 */
@Tag(name = "基础资料-仓库")
@RestController
@RequestMapping("/basedata/warehouses")
@RequiredArgsConstructor
public class BaseWarehouseController {

    private final BaseWarehouseService warehouseService;
    private final BaseLocationService locationService;

    /** 分页查询仓库 */
    @Operation(summary = "分页查询仓库")
    @RequirePerm("basedata:warehouse:list")
    @GetMapping
    public Result<PageResult<BaseWarehouseVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(warehouseService.page(pageNum, pageSize, keyword));
    }

    /** 启用仓库列表（出入库选择器用） */
    @Operation(summary = "启用仓库列表(下拉)")
    @GetMapping("/enabled")
    public Result<List<BaseWarehouseVO>> listEnabled() {
        return Result.ok(warehouseService.listEnabled());
    }

    /** 仓库详情 */
    @Operation(summary = "仓库详情")
    @RequirePerm("basedata:warehouse:list")
    @GetMapping("/{id}")
    public Result<BaseWarehouseVO> getById(@PathVariable Long id) {
        return Result.ok(warehouseService.getById(id));
    }

    /** 新增仓库（编码留空自动生成） */
    @Operation(summary = "新增仓库")
    @RequirePerm("basedata:warehouse:add")
    @OperLog(module = "仓库管理", operation = "新增仓库")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody BaseWarehouseForm form) {
        return Result.ok(warehouseService.create(form));
    }

    /** 修改仓库 */
    @Operation(summary = "修改仓库")
    @RequirePerm("basedata:warehouse:edit")
    @OperLog(module = "仓库管理", operation = "修改仓库")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BaseWarehouseForm form) {
        warehouseService.update(id, form);
        return Result.ok();
    }

    /** 删除仓库（存在库位抛 DATA_IN_USE） */
    @Operation(summary = "删除仓库")
    @RequirePerm("basedata:warehouse:delete")
    @OperLog(module = "仓库管理", operation = "删除仓库")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return Result.ok();
    }

    /** 查询指定仓库下全部库位 */
    @Operation(summary = "仓库下库位列表")
    @RequirePerm("basedata:warehouse:list")
    @GetMapping("/{warehouseId}/locations")
    public Result<List<BaseLocationVO>> listLocations(@PathVariable Long warehouseId) {
        return Result.ok(locationService.listByWarehouse(warehouseId));
    }

    /** 新增库位 */
    @Operation(summary = "新增库位")
    @RequirePerm("basedata:warehouse:add")
    @OperLog(module = "仓库管理", operation = "新增库位")
    @Idempotent
    @PostMapping("/locations")
    public Result<Long> createLocation(@Valid @RequestBody BaseLocationForm form) {
        return Result.ok(locationService.create(form));
    }

    /** 修改库位 */
    @Operation(summary = "修改库位")
    @RequirePerm("basedata:warehouse:edit")
    @OperLog(module = "仓库管理", operation = "修改库位")
    @Idempotent
    @PutMapping("/locations/{id}")
    public Result<Void> updateLocation(@PathVariable Long id, @Valid @RequestBody BaseLocationForm form) {
        locationService.update(id, form);
        return Result.ok();
    }

    /** 删除库位 */
    @Operation(summary = "删除库位")
    @RequirePerm("basedata:warehouse:delete")
    @OperLog(module = "仓库管理", operation = "删除库位")
    @Idempotent
    @DeleteMapping("/locations/{id}")
    public Result<Void> deleteLocation(@PathVariable Long id) {
        locationService.delete(id);
        return Result.ok();
    }
}
