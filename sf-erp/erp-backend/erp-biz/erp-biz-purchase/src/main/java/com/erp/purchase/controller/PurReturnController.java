package com.erp.purchase.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.purchase.dto.form.PurReturnForm;
import com.erp.purchase.dto.query.PurReturnQuery;
import com.erp.purchase.dto.vo.PurReturnVO;
import com.erp.purchase.dto.vo.ReturnPreviewVO;
import com.erp.purchase.service.PurReturnService;
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
 * 采购退货单接口。
 */
@Tag(name = "采购退货单")
@RestController
@RequestMapping("/purchase/returns")
@RequiredArgsConstructor
public class PurReturnController {

    private final PurReturnService purReturnService;

    /** 分页查询采购退货单 */
    @Operation(summary = "分页查询采购退货单")
    @RequirePerm("purchase:return:list")
    @GetMapping
    public Result<PageResult<PurReturnVO>> page(PurReturnQuery query) {
        return Result.ok(purReturnService.page(query));
    }

    /** 采购退货单详情（主表+明细） */
    @Operation(summary = "采购退货单详情")
    @RequirePerm("purchase:return:list")
    @GetMapping("/{id}")
    public Result<PurReturnVO> detail(@PathVariable Long id) {
        return Result.ok(purReturnService.detail(id));
    }

    /** 选单：按已审核入库单预填退货表单（建议退货=入库数量-已退） */
    @Operation(summary = "按入库单预填充退货单")
    @RequirePerm("purchase:return:list")
    @GetMapping("/from-instock/{instockId}")
    public Result<ReturnPreviewVO> previewFromInstock(@PathVariable Long instockId) {
        return Result.ok(purReturnService.previewFromInstock(instockId));
    }

    /** 新增采购退货单（草稿） */
    @Operation(summary = "新增采购退货单")
    @RequirePerm("purchase:return:add")
    @OperLog(module = "采购管理", operation = "新增采购退货单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PurReturnForm form) {
        return Result.ok(purReturnService.create(form));
    }

    /** 修改采购退货单（仅草稿） */
    @Operation(summary = "修改采购退货单")
    @RequirePerm("purchase:return:edit")
    @OperLog(module = "采购管理", operation = "修改采购退货单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PurReturnForm form) {
        purReturnService.update(id, form);
        return Result.ok();
    }

    /** 审核采购退货单（事务：反向库存流水+扣库存+订单回写） */
    @Operation(summary = "审核采购退货单")
    @RequirePerm("purchase:return:audit")
    @OperLog(module = "采购管理", operation = "审核采购退货单")
    @Idempotent
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        purReturnService.audit(id);
        return Result.ok();
    }

    /** 删除采购退货单（仅草稿，逻辑删除） */
    @Operation(summary = "删除采购退货单")
    @RequirePerm("purchase:return:delete")
    @OperLog(module = "采购管理", operation = "删除采购退货单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purReturnService.delete(id);
        return Result.ok();
    }
}
