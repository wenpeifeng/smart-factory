package com.erp.basedata.controller;

import com.erp.basedata.dto.form.PriceContractForm;
import com.erp.basedata.dto.vo.PriceContractVO;
import com.erp.basedata.service.BasePriceContractService;
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
 * 采购合同价接口（价格中心-合同价 Tab）。
 */
@Tag(name = "基础资料-采购合同价")
@RestController
@RequestMapping("/basedata/price-contracts")
@RequiredArgsConstructor
public class PriceContractController {

    private final BasePriceContractService contractService;

    /** 合同价分页 */
    @Operation(summary = "合同价分页")
    @RequirePerm("basedata:price:list")
    @GetMapping
    public Result<PageResult<PriceContractVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Integer status) {
        return Result.ok(contractService.page(pageNum, pageSize, supplierId, materialId, status));
    }

    /** 新增合同价 */
    @Operation(summary = "新增合同价")
    @RequirePerm("basedata:price:add")
    @OperLog(module = "价格管理", operation = "新增采购合同价")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PriceContractForm form) {
        return Result.ok(contractService.create(form));
    }

    /** 修改合同价 */
    @Operation(summary = "修改合同价")
    @RequirePerm("basedata:price:edit")
    @OperLog(module = "价格管理", operation = "修改采购合同价")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PriceContractForm form) {
        contractService.update(id, form);
        return Result.ok();
    }

    /** 删除合同价 */
    @Operation(summary = "删除合同价")
    @RequirePerm("basedata:price:delete")
    @OperLog(module = "价格管理", operation = "删除采购合同价")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        contractService.delete(id);
        return Result.ok();
    }
}
