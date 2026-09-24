package com.erp.basedata.controller;

import com.erp.basedata.dto.form.PriceQtyForm;
import com.erp.basedata.dto.vo.PriceQtyVO;
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
 * 销售批量价接口（价格中心-批量价 Tab）。
 */
@Tag(name = "基础资料-销售批量价")
@RestController
@RequestMapping("/basedata/price-qtys")
@RequiredArgsConstructor
public class PriceQtyController {

    private final SalPriceStrategyService strategyService;

    /** 批量价分页 */
    @Operation(summary = "批量价分页")
    @RequirePerm("basedata:price:list")
    @GetMapping
    public Result<PageResult<PriceQtyVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Integer status) {
        return Result.ok(strategyService.pageQtys(pageNum, pageSize, materialId, status));
    }

    /** 新增批量价（uk 物料+起订量） */
    @Operation(summary = "新增批量价")
    @RequirePerm("basedata:price:add")
    @OperLog(module = "价格管理", operation = "新增销售批量价")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PriceQtyForm form) {
        return Result.ok(strategyService.createQty(form));
    }

    /** 修改批量价 */
    @Operation(summary = "修改批量价")
    @RequirePerm("basedata:price:edit")
    @OperLog(module = "价格管理", operation = "修改销售批量价")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PriceQtyForm form) {
        strategyService.updateQty(id, form);
        return Result.ok();
    }

    /** 删除批量价 */
    @Operation(summary = "删除批量价")
    @RequirePerm("basedata:price:delete")
    @OperLog(module = "价格管理", operation = "删除销售批量价")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        strategyService.deleteQty(id);
        return Result.ok();
    }
}
