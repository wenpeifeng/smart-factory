package com.erp.purchase.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.purchase.dto.form.PurInstockForm;
import com.erp.purchase.dto.query.PurInstockQuery;
import com.erp.purchase.dto.vo.InstockPreviewVO;
import com.erp.purchase.dto.vo.PurInstockVO;
import com.erp.purchase.service.PurInstockService;
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
 * 采购入库单接口。
 */
@Tag(name = "采购入库单")
@RestController
@RequestMapping("/purchase/instock")
@RequiredArgsConstructor
public class PurInstockController {

    private final PurInstockService purInstockService;

    /** 分页查询采购入库单 */
    @Operation(summary = "分页查询采购入库单")
    @RequirePerm("purchase:instock:list")
    @GetMapping
    public Result<PageResult<PurInstockVO>> page(PurInstockQuery query) {
        return Result.ok(purInstockService.page(query));
    }

    /** 采购入库单详情（主表+明细） */
    @Operation(summary = "采购入库单详情")
    @RequirePerm("purchase:instock:list")
    @GetMapping("/{id}")
    public Result<PurInstockVO> detail(@PathVariable Long id) {
        return Result.ok(purInstockService.detail(id));
    }

    /** 选单：按已审核采购订单预填充入库表单（建议实收=订单数量-已入库） */
    @Operation(summary = "按采购订单预填充入库单")
    @RequirePerm("purchase:instock:list")
    @GetMapping("/from-order/{orderId}")
    public Result<InstockPreviewVO> previewFromOrder(@PathVariable Long orderId) {
        return Result.ok(purInstockService.previewFromOrder(orderId));
    }

    /** 新增采购入库单（草稿，禁止超收） */
    @Operation(summary = "新增采购入库单")
    @RequirePerm("purchase:instock:add")
    @OperLog(module = "采购管理", operation = "新增采购入库单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PurInstockForm form) {
        return Result.ok(purInstockService.create(form));
    }

    /** 修改采购入库单（仅草稿） */
    @Operation(summary = "修改采购入库单")
    @RequirePerm("purchase:instock:edit")
    @OperLog(module = "采购管理", operation = "修改采购入库单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PurInstockForm form) {
        purInstockService.update(id, form);
        return Result.ok();
    }

    /** 审核采购入库单（事务：库存流水+库存+应付+订单回写） */
    @Operation(summary = "审核采购入库单")
    @RequirePerm("purchase:instock:audit")
    @OperLog(module = "采购管理", operation = "审核采购入库单")
    @Idempotent
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        purInstockService.audit(id);
        return Result.ok();
    }

    /** 删除采购入库单（仅草稿，逻辑删除） */
    @Operation(summary = "删除采购入库单")
    @RequirePerm("purchase:instock:delete")
    @OperLog(module = "采购管理", operation = "删除采购入库单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purInstockService.delete(id);
        return Result.ok();
    }
}
