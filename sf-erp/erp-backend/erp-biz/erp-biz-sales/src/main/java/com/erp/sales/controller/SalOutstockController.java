package com.erp.sales.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.sales.dto.form.SalOutstockForm;
import com.erp.sales.dto.vo.SalOutstockDetailBundle;
import com.erp.sales.dto.vo.SalOutstockVO;
import com.erp.sales.service.SalOutstockService;
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
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 销售出库单接口。
 * <p>
 * 审核执行：扣库存（不足报错回滚）→ 写库存流水 → 生成应收 → 回写订单数量与状态。
 */
@Tag(name = "销售出库单")
@RestController
@RequestMapping("/sales/outstock")
@RequiredArgsConstructor
public class SalOutstockController {

    private final SalOutstockService outstockService;

    /** 分页查询销售出库单（keyword 匹配出库单号/订单号/客户名称） */
    @Operation(summary = "分页查询销售出库单")
    @RequirePerm("sales:outstock:list")
    @GetMapping
    public Result<PageResult<SalOutstockVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.ok(outstockService.page(pageNum, pageSize, keyword, status));
    }

    /** 查询出库单详情（表头 + 明细行，明细含剩余可退数量） */
    @Operation(summary = "查询销售出库单详情")
    @RequirePerm("sales:outstock:list")
    @GetMapping("/{id}")
    public Result<SalOutstockDetailBundle> detail(@PathVariable Long id) {
        return Result.ok(outstockService.getDetail(id));
    }

    /** 新增销售出库单（从销售订单选单生成，草稿态） */
    @Operation(summary = "新增销售出库单")
    @RequirePerm("sales:outstock:add")
    @OperLog(module = "销售管理", operation = "新增销售出库单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SalOutstockForm form) {
        return Result.ok(outstockService.create(form));
    }

    /** 审核销售出库单（扣库存/写流水/生应收） */
    @Operation(summary = "审核销售出库单")
    @RequirePerm("sales:outstock:audit")
    @OperLog(module = "销售管理", operation = "审核销售出库单")
    @Idempotent
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        outstockService.audit(id);
        return Result.ok();
    }

    /** 删除销售出库单（仅草稿，逻辑删除） */
    @Operation(summary = "删除销售出库单")
    @RequirePerm("sales:outstock:delete")
    @OperLog(module = "销售管理", operation = "删除销售出库单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        outstockService.delete(id);
        return Result.ok();
    }
}
