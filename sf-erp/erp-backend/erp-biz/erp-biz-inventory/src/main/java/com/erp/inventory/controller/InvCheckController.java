package com.erp.inventory.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.inventory.dto.form.CheckActualForm;
import com.erp.inventory.dto.form.CheckCreateForm;
import com.erp.inventory.dto.request.CheckQuery;
import com.erp.inventory.dto.vo.CheckDocDetailVO;
import com.erp.inventory.dto.vo.CheckVO;
import com.erp.inventory.service.InvCheckService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 库存盘点接口：生成快照 → 录实盘 → 完成调平。
 */
@Tag(name = "库存管理-库存盘点")
@RestController
@RequestMapping("/inventory/checks")
@RequiredArgsConstructor
public class InvCheckController {

    private final InvCheckService checkService;

    /** 分页查询盘点单 */
    @Operation(summary = "盘点单分页查询")
    @RequirePerm("inventory:check:list")
    @GetMapping
    public Result<PageResult<CheckVO>> page(CheckQuery query) {
        return Result.ok(checkService.page(query));
    }

    /** 盘点单详情（头 + 快照明细行） */
    @Operation(summary = "盘点单详情")
    @RequirePerm("inventory:check:list")
    @GetMapping("/{id}")
    public Result<CheckDocDetailVO> detail(@PathVariable Long id) {
        return Result.ok(checkService.detail(id));
    }

    /** 创建盘点单：选仓库生成库存快照 */
    @Operation(summary = "创建盘点单(生成库存快照)")
    @RequirePerm("inventory:check:add")
    @OperLog(module = "库存管理", operation = "新增盘点单")
    @Idempotent
    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody CheckCreateForm form) {
        Long id = checkService.create(form);
        return Result.ok(Map.of("id", id));
    }

    /** 保存实盘数量（可分批），自动计算差异 */
    @Operation(summary = "保存盘点实盘数量")
    @RequirePerm("inventory:check:edit")
    @OperLog(module = "库存管理", operation = "保存盘点实数")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> saveActual(@PathVariable Long id, @Valid @RequestBody CheckActualForm form) {
        checkService.saveActual(id, form);
        return Result.ok();
    }

    /** 完成盘点：差异行生成 ADJUST 流水调平库存 */
    @Operation(summary = "完成盘点(差异调平)")
    @RequirePerm("inventory:check:audit")
    @OperLog(module = "库存管理", operation = "完成盘点")
    @Idempotent
    @PostMapping("/{id}/complete")
    public Result<Map<String, Integer>> complete(@PathVariable Long id) {
        int adjusted = checkService.complete(id);
        return Result.ok(Map.of("adjusted", adjusted));
    }

    /** 删除盘点单（仅盘点中） */
    @Operation(summary = "删除盘点单(仅盘点中)")
    @RequirePerm("inventory:check:delete")
    @OperLog(module = "库存管理", operation = "删除盘点单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        checkService.delete(id);
        return Result.ok();
    }
}
