package com.erp.org.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.org.dto.form.AssignWarehouseForm;
import com.erp.org.dto.form.FactoryForm;
import com.erp.org.dto.form.StatusForm;
import com.erp.org.dto.request.FactoryQuery;
import com.erp.org.dto.vo.FactoryVO;
import com.erp.org.dto.vo.WarehouseBelongVO;
import com.erp.org.service.OrgFactoryService;
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

import java.util.List;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 工厂档案接口（轻量多组织：多工厂 + 仓库归属）。
 * <p>
 * 路径 id 为准，覆盖表单内 id；出参一律 VO。
 */
@Tag(name = "组织管理-工厂档案")
@RestController
@RequestMapping("/org/factories")
@RequiredArgsConstructor
public class OrgFactoryController {

    private final OrgFactoryService factoryService;

    /** 工厂分页（名称/编码模糊 + 状态，带归属仓库数） */
    @Operation(summary = "分页查询工厂")
    @RequirePerm("org:factory:list")
    @GetMapping
    public Result<PageResult<FactoryVO>> page(@ModelAttribute FactoryQuery query) {
        return Result.ok(factoryService.page(query));
    }

    /** 启用工厂清单（下拉选择器用） */
    @Operation(summary = "启用工厂列表(下拉)")
    @GetMapping("/enabled")
    public Result<List<FactoryVO>> listEnabled() {
        return Result.ok(factoryService.listEnabled());
    }

    /** 工厂详情 */
    @Operation(summary = "工厂详情")
    @RequirePerm("org:factory:list")
    @GetMapping("/{id}")
    public Result<FactoryVO> detail(@PathVariable Long id) {
        return Result.ok(factoryService.getDetail(id));
    }

    /** 新增工厂（编码留空自动生成 FC+yyyyMMdd+3位流水） */
    @Operation(summary = "新增工厂")
    @RequirePerm("org:factory:add")
    @OperLog(module = "组织间结算", operation = "新增工厂")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody FactoryForm form) {
        return Result.ok(factoryService.create(form));
    }

    /** 修改工厂（编码不可改） */
    @Operation(summary = "修改工厂")
    @RequirePerm("org:factory:edit")
    @OperLog(module = "组织间结算", operation = "修改工厂")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody FactoryForm form) {
        factoryService.update(id, form);
        return Result.ok();
    }

    /** 删除工厂（逻辑删除；总厂与仍有仓库归属的工厂不可删） */
    @Operation(summary = "删除工厂")
    @RequirePerm("org:factory:delete")
    @OperLog(module = "组织间结算", operation = "删除工厂")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        factoryService.delete(id);
        return Result.ok();
    }

    /** 启用/停用工厂 */
    @Operation(summary = "启用停用工厂")
    @RequirePerm("org:factory:edit")
    @OperLog(module = "组织间结算", operation = "启停工厂")
    @Idempotent
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        factoryService.updateStatus(id, form.getStatus());
        return Result.ok();
    }

    /** 工厂下仓库清单（JOIN base_warehouse 只读） */
    @Operation(summary = "工厂下仓库清单")
    @RequirePerm("org:factory:list")
    @GetMapping("/{id}/warehouses")
    public Result<List<WarehouseBelongVO>> listWarehouses(@PathVariable Long id) {
        return Result.ok(factoryService.listWarehouses(id));
    }

    /** 仓库归属调整（回写 base_warehouse.factory_id） */
    @Operation(summary = "仓库归属调整")
    @RequirePerm("org:factory:edit")
    @OperLog(module = "组织间结算", operation = "分配工厂仓库")
    @Idempotent
    @PutMapping("/assign-warehouse")
    public Result<Void> assignWarehouse(@Valid @RequestBody AssignWarehouseForm form) {
        factoryService.assignWarehouse(form);
        return Result.ok();
    }
}
