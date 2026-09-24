package com.erp.sales.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.sales.dto.form.SalOrderForm;
import com.erp.sales.dto.vo.SalOrderDetailBundle;
import com.erp.sales.dto.vo.SalOrderVO;
import com.erp.sales.service.SalOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 销售订单接口。
 * <p>
 * 状态机：0草稿 →(审核)→ 10已审核 →(出库回写)→ 20部分出库/30已完成；10/20 →(关闭)→ 90已关闭。
 */
@Tag(name = "销售订单")
@RestController
@RequestMapping("/sales/orders")
@RequiredArgsConstructor
public class SalOrderController {

    private final SalOrderService orderService;

    /** 分页查询销售订单（keyword 匹配订单号/客户名称） */
    @Operation(summary = "分页查询销售订单")
    @RequirePerm("sales:order:list")
    @GetMapping
    public Result<PageResult<SalOrderVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.ok(orderService.page(pageNum, pageSize, keyword, status));
    }

    /** 查询订单详情（表头 + 明细行，明细含剩余可出数量） */
    @Operation(summary = "查询销售订单详情")
    @RequirePerm("sales:order:list")
    @GetMapping("/{id}")
    public Result<SalOrderDetailBundle> detail(@PathVariable Long id) {
        return Result.ok(orderService.getDetail(id));
    }

    /** 新增销售订单（草稿态） */
    @Operation(summary = "新增销售订单")
    @RequirePerm("sales:order:add")
    @OperLog(module = "销售管理", operation = "新增销售订单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SalOrderForm form) {
        return Result.ok(orderService.create(form));
    }

    /** 修改销售订单（仅草稿） */
    @Operation(summary = "修改销售订单")
    @RequirePerm("sales:order:edit")
    @OperLog(module = "销售管理", operation = "修改销售订单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SalOrderForm form) {
        orderService.update(id, form);
        return Result.ok();
    }

    /** 审核销售订单（三段式：免审直过/提交审批/审批通过后执行审核；含信用额度校验） */
    @Operation(summary = "审核销售订单")
    @RequirePerm("sales:order:audit")
    @OperLog(module = "销售管理", operation = "审核销售订单")
    @Idempotent
    @PutMapping("/{id}/audit")
    public Result<String> audit(@PathVariable Long id) {
        return Result.ok(orderService.audit(id));
    }

    /** 关闭销售订单（剩余数量不再执行） */
    @Operation(summary = "关闭销售订单")
    @RequirePerm("sales:order:audit")
    @OperLog(module = "销售管理", operation = "关闭销售订单")
    @Idempotent
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        orderService.close(id);
        return Result.ok();
    }

    /** 删除销售订单（仅草稿，逻辑删除） */
    @Operation(summary = "删除销售订单")
    @RequirePerm("sales:order:delete")
    @OperLog(module = "销售管理", operation = "删除销售订单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Parameter(description = "订单 ID") Long id) {
        orderService.delete(id);
        return Result.ok();
    }
}
