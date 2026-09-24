package com.erp.basedata.controller;

import com.erp.basedata.dto.form.BaseZoneForm;
import com.erp.basedata.dto.vo.BaseZoneVO;
import com.erp.basedata.dto.vo.LocationTreeNodeVO;
import com.erp.basedata.service.BaseZoneService;
import com.erp.common.result.Result;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库区接口（V421，仓库→库区→库位三级）。
 * <p>
 * 独立文件而非追加在 BaseWarehouseController，避免并行改动冲突；
 * location-tree 挂在 /basedata/warehouses/{warehouseId}/location-tree 语义路径下
 * （与仓库 Controller 的 /locations 子路径不冲突）。
 */
@Tag(name = "基础资料-库区")
@RestController
@RequiredArgsConstructor
public class BaseZoneController {

    private final BaseZoneService zoneService;

    /** 仓库下库区列表 */
    @Operation(summary = "仓库下库区列表")
    @RequirePerm("basedata:warehouse:list")
    @GetMapping("/basedata/warehouses/{warehouseId}/zones")
    public Result<List<BaseZoneVO>> listByWarehouse(@PathVariable Long warehouseId) {
        return Result.ok(zoneService.listByWarehouse(warehouseId));
    }

    /** 仓库→库区→库位三级树（未挂库区的库位归"未分区"组） */
    @Operation(summary = "仓库库位树")
    @RequirePerm("basedata:warehouse:list")
    @GetMapping("/basedata/warehouses/{warehouseId}/location-tree")
    public Result<List<LocationTreeNodeVO>> locationTree(@PathVariable Long warehouseId) {
        return Result.ok(zoneService.locationTree(warehouseId));
    }

    /** 新增库区 */
    @Operation(summary = "新增库区")
    @RequirePerm("basedata:warehouse:add")
    @OperLog(module = "仓库管理", operation = "新增库区")
    @Idempotent
    @PostMapping("/basedata/zones")
    public Result<Long> create(@Valid @RequestBody BaseZoneForm form) {
        return Result.ok(zoneService.create(form));
    }

    /** 修改库区 */
    @Operation(summary = "修改库区")
    @RequirePerm("basedata:warehouse:edit")
    @OperLog(module = "仓库管理", operation = "修改库区")
    @Idempotent
    @PutMapping("/basedata/zones/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BaseZoneForm form) {
        zoneService.update(id, form);
        return Result.ok();
    }

    /** 删除库区（库区下有库位时拒绝） */
    @Operation(summary = "删除库区")
    @RequirePerm("basedata:warehouse:delete")
    @OperLog(module = "仓库管理", operation = "删除库区")
    @Idempotent
    @DeleteMapping("/basedata/zones/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        zoneService.delete(id);
        return Result.ok();
    }
}
