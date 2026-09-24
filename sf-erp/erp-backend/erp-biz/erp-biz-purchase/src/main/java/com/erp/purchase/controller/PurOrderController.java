package com.erp.purchase.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.purchase.dto.form.PurOrderForm;
import com.erp.purchase.dto.query.PurOrderQuery;
import com.erp.purchase.dto.vo.PurOrderVO;
import com.erp.purchase.service.PurOrderService;
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
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 采购订单接口。
 * Controller 只做参数接收/校验/组装响应，业务逻辑一律在 Service。
 */
@Tag(name = "采购订单")
@RestController
@RequestMapping("/purchase/orders")
@RequiredArgsConstructor
public class PurOrderController {

    private final PurOrderService purOrderService;

    /** 分页查询采购订单（单号模糊/供应商/状态/日期区间） */
    @Operation(summary = "分页查询采购订单")
    @RequirePerm("purchase:order:list")
    @GetMapping
    public Result<PageResult<PurOrderVO>> page(PurOrderQuery query) {
        return Result.ok(purOrderService.page(query));
    }

    /** 采购订单详情（主表+明细） */
    @Operation(summary = "采购订单详情")
    @RequirePerm("purchase:order:list")
    @GetMapping("/{id}")
    public Result<PurOrderVO> detail(@PathVariable Long id) {
        return Result.ok(purOrderService.detail(id));
    }

    /** 新增采购订单（草稿） */
    @Operation(summary = "新增采购订单")
    @RequirePerm("purchase:order:add")
    @OperLog(module = "采购管理", operation = "新增采购订单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PurOrderForm form) {
        return Result.ok(purOrderService.create(form));
    }

    /** 修改采购订单（仅草稿） */
    @Operation(summary = "修改采购订单")
    @RequirePerm("purchase:order:edit")
    @OperLog(module = "采购管理", operation = "修改采购订单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PurOrderForm form) {
        purOrderService.update(id, form);
        return Result.ok();
    }

    /** 审核采购订单（三段式：免审直过/提交审批/审批通过后执行审核） */
    @Operation(summary = "审核采购订单")
    @RequirePerm("purchase:order:audit")
    @OperLog(module = "采购管理", operation = "审核采购订单")
    @Idempotent
    @PutMapping("/{id}/audit")
    public Result<String> audit(@PathVariable Long id) {
        return Result.ok(purOrderService.audit(id));
    }

    /** 关闭采购订单（已审核/部分入库->已关闭） */
    @Operation(summary = "关闭采购订单")
    @RequirePerm("purchase:order:audit")
    @OperLog(module = "采购管理", operation = "关闭采购订单")
    @Idempotent
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        purOrderService.close(id);
        return Result.ok();
    }

    /** 删除采购订单（仅草稿，逻辑删除） */
    @Operation(summary = "删除采购订单")
    @RequirePerm("purchase:order:delete")
    @OperLog(module = "采购管理", operation = "删除采购订单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purOrderService.delete(id);
        return Result.ok();
    }
}
