package com.erp.basedata.controller;

import com.erp.basedata.dto.form.PriceLevelForm;
import com.erp.basedata.dto.vo.PriceLevelVO;
import com.erp.basedata.service.SalPriceStrategyService;
import com.erp.common.result.PageResult;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售等级价接口（价格中心-等级价 Tab）。
 */
@Tag(name = "基础资料-销售等级价")
@RestController
@RequestMapping("/basedata/price-levels")
@RequiredArgsConstructor
public class PriceLevelController {

    private final SalPriceStrategyService strategyService;

    /** 等级价分页 */
    @Operation(summary = "等级价分页")
    @RequirePerm("basedata:price:list")
    @GetMapping
    public Result<PageResult<PriceLevelVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Integer status) {
        return Result.ok(strategyService.pageLevels(pageNum, pageSize, materialId, level, status));
    }

    /** 新增等级价（uk 物料+等级） */
    @Operation(summary = "新增等级价")
    @RequirePerm("basedata:price:add")
    @OperLog(module = "价格管理", operation = "新增销售等级价")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PriceLevelForm form) {
        return Result.ok(strategyService.createLevel(form));
    }

    /** 修改等级价 */
    @Operation(summary = "修改等级价")
    @RequirePerm("basedata:price:edit")
    @OperLog(module = "价格管理", operation = "修改销售等级价")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PriceLevelForm form) {
        strategyService.updateLevel(id, form);
        return Result.ok();
    }

    /** 删除等级价 */
    @Operation(summary = "删除等级价")
    @RequirePerm("basedata:price:delete")
    @OperLog(module = "价格管理", operation = "删除销售等级价")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        strategyService.deleteLevel(id);
        return Result.ok();
    }
}
