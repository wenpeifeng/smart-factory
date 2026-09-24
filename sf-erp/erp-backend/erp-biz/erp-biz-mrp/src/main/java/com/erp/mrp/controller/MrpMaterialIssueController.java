package com.erp.mrp.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.mrp.dto.form.IssueForm;
import com.erp.mrp.dto.vo.MaterialIssueVO;
import com.erp.mrp.service.MrpMaterialIssueService;
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
 * 生产领料接口：按 BOM 自动带出、审核扣库存。
 */
@Tag(name = "生产制造-生产领料")
@RestController
@RequestMapping("/mrp/material-issues")
@RequiredArgsConstructor
public class MrpMaterialIssueController {

    private final MrpMaterialIssueService issueService;

    /** 分页查询领料单（可按订单过滤） */
    @Operation(summary = "分页查询领料单")
    @RequirePerm("mrp:issue:list")
    @GetMapping
    public Result<PageResult<MaterialIssueVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long workOrderId,
            @RequestParam(required = false) String status) {
        return Result.ok(issueService.page(pageNum, pageSize, workOrderId, status));
    }

    /** 领料单详情（含明细） */
    @Operation(summary = "领料单详情")
    @RequirePerm("mrp:issue:list")
    @GetMapping("/{id}")
    public Result<MaterialIssueVO> detail(@PathVariable Long id) {
        return Result.ok(issueService.detail(id));
    }

    /** 创建草稿领料单（items 空=按BOM全量带出） */
    @Operation(summary = "创建领料单")
    @RequirePerm("mrp:issue:add")
    @OperLog(module = "生产制造", operation = "新增领料单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody IssueForm form) {
        return Result.ok(issueService.create(form));
    }

    /** 修改草稿领料单 */
    @Operation(summary = "修改领料单")
    @RequirePerm("mrp:issue:edit")
    @OperLog(module = "生产制造", operation = "修改领料单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody IssueForm form) {
        issueService.update(id, form);
        return Result.ok();
    }

    /** 审核领料单（扣库存+写库存流水） */
    @Operation(summary = "审核领料单")
    @RequirePerm("mrp:issue:audit")
    @OperLog(module = "生产制造", operation = "审核领料单")
    @Idempotent
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        issueService.audit(id);
        return Result.ok();
    }

    /** 删除草稿领料单 */
    @Operation(summary = "删除领料单")
    @RequirePerm("mrp:issue:delete")
    @OperLog(module = "生产制造", operation = "删除领料单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        issueService.delete(id);
        return Result.ok();
    }
}
