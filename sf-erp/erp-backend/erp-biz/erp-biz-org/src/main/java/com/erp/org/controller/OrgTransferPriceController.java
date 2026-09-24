package com.erp.org.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.org.dto.form.StatusForm;
import com.erp.org.dto.form.TransferPriceForm;
import com.erp.org.dto.request.TransferPriceQuery;
import com.erp.org.dto.vo.TransferPriceVO;
import com.erp.org.service.OrgTransferPriceService;
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
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 组织间调拨价目接口（物料×调出工厂×调入工厂 定价规则）。
 */
@Tag(name = "组织管理-调拨价目")
@RestController
@RequestMapping("/org/transfer-prices")
@RequiredArgsConstructor
public class OrgTransferPriceController {

    private final OrgTransferPriceService priceService;

    /** 价目分页（物料/调出工厂/调入工厂/状态） */
    @Operation(summary = "分页查询价目")
    @RequirePerm("org:price:list")
    @GetMapping
    public Result<PageResult<TransferPriceVO>> page(@ModelAttribute TransferPriceQuery query) {
        return Result.ok(priceService.page(query));
    }

    /** 新增价目（线路唯一） */
    @Operation(summary = "新增价目")
    @RequirePerm("org:price:add")
    @OperLog(module = "组织间结算", operation = "新增转移价格")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TransferPriceForm form) {
        return Result.ok(priceService.create(form));
    }

    /** 修改价目 */
    @Operation(summary = "修改价目")
    @RequirePerm("org:price:edit")
    @OperLog(module = "组织间结算", operation = "修改转移价格")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TransferPriceForm form) {
        priceService.update(id, form);
        return Result.ok();
    }

    /** 删除价目（逻辑删除） */
    @Operation(summary = "删除价目")
    @RequirePerm("org:price:delete")
    @OperLog(module = "组织间结算", operation = "删除转移价格")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        priceService.delete(id);
        return Result.ok();
    }

    /** 启用/停用价目 */
    @Operation(summary = "启用停用价目")
    @RequirePerm("org:price:edit")
    @OperLog(module = "组织间结算", operation = "启停转移价格")
    @Idempotent
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        priceService.updateStatus(id, form.getStatus());
        return Result.ok();
    }
}
