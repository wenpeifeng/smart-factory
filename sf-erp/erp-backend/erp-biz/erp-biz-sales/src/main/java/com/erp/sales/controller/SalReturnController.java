package com.erp.sales.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.sales.dto.form.SalReturnForm;
import com.erp.sales.dto.vo.SalReturnDetailBundle;
import com.erp.sales.dto.vo.SalReturnVO;
import com.erp.sales.service.SalReturnService;
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
 * 销售退货单接口。
 * <p>
 * 审核执行：回补库存 → 写正数流水 → 生成红字（负金额）应收 → 回写出库行已退数量。
 */
@Tag(name = "销售退货单")
@RestController
@RequestMapping("/sales/returns")
@RequiredArgsConstructor
public class SalReturnController {

    private final SalReturnService returnService;

    /** 分页查询销售退货单（keyword 匹配退货单号/出库单号/客户名称） */
    @Operation(summary = "分页查询销售退货单")
    @RequirePerm("sales:return:list")
    @GetMapping
    public Result<PageResult<SalReturnVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.ok(returnService.page(pageNum, pageSize, keyword, status));
    }

    /** 查询退货单详情（表头 + 明细行） */
    @Operation(summary = "查询销售退货单详情")
    @RequirePerm("sales:return:list")
    @GetMapping("/{id}")
    public Result<SalReturnDetailBundle> detail(@PathVariable Long id) {
        return Result.ok(returnService.getDetail(id));
    }

    /** 新增销售退货单（引用销售出库单，草稿态） */
    @Operation(summary = "新增销售退货单")
    @RequirePerm("sales:return:add")
    @OperLog(module = "销售管理", operation = "新增销售退货单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SalReturnForm form) {
        return Result.ok(returnService.create(form));
    }

    /** 审核销售退货单（回补库存/写流水/生红字应收） */
    @Operation(summary = "审核销售退货单")
    @RequirePerm("sales:return:audit")
    @OperLog(module = "销售管理", operation = "审核销售退货单")
    @Idempotent
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        returnService.audit(id);
        return Result.ok();
    }

    /** 删除销售退货单（仅草稿，逻辑删除） */
    @Operation(summary = "删除销售退货单")
    @RequirePerm("sales:return:delete")
    @OperLog(module = "销售管理", operation = "删除销售退货单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        returnService.delete(id);
        return Result.ok();
    }
}
