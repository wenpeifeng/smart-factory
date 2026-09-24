package com.erp.mrp.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.mrp.dto.form.RoutingCopyForm;
import com.erp.mrp.dto.form.RoutingForm;
import com.erp.mrp.dto.vo.RoutingTreeVO;
import com.erp.mrp.dto.vo.RoutingVO;
import com.erp.mrp.service.MrpRoutingService;
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
 * 工艺路线接口：CRUD、复制新版本、审核/停用、工序序列树形查看。
 * 已审核(ACTIVE)路线参与生产订单下达时的工序任务生成（取最新一条）。
 */
@Tag(name = "生产制造-工艺路线")
@RestController
@RequestMapping("/mrp/routings")
@RequiredArgsConstructor
public class MrpRoutingController {

    private final MrpRoutingService routingService;

    /** 分页查询工艺路线 */
    @Operation(summary = "分页查询工艺路线")
    @RequirePerm("mrp:routing:list")
    @GetMapping
    public Result<PageResult<RoutingVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(routingService.page(pageNum, pageSize, keyword, status));
    }

    /** 工艺路线详情（含步骤明细） */
    @Operation(summary = "工艺路线详情")
    @RequirePerm("mrp:routing:list")
    @GetMapping("/{id}")
    public Result<RoutingVO> detail(@PathVariable Long id) {
        return Result.ok(routingService.detail(id));
    }

    /** 树形查看（工序序列） */
    @Operation(summary = "工艺路线工序序列")
    @RequirePerm("mrp:routing:list")
    @GetMapping("/{id}/tree")
    public Result<RoutingTreeVO> tree(@PathVariable Long id) {
        return Result.ok(routingService.tree(id));
    }

    /** 新建工艺路线（草稿） */
    @Operation(summary = "新建工艺路线")
    @RequirePerm("mrp:routing:add")
    @OperLog(module = "生产制造", operation = "新增工艺路线")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody RoutingForm form) {
        return Result.ok(routingService.create(form));
    }

    /** 修改工艺路线（仅草稿，步骤整体重建） */
    @Operation(summary = "修改工艺路线")
    @RequirePerm("mrp:routing:edit")
    @OperLog(module = "生产制造", operation = "修改工艺路线")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoutingForm form) {
        routingService.update(id, form);
        return Result.ok();
    }

    /** 删除工艺路线（逻辑删除） */
    @Operation(summary = "删除工艺路线")
    @RequirePerm("mrp:routing:delete")
    @OperLog(module = "生产制造", operation = "删除工艺路线")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        routingService.delete(id);
        return Result.ok();
    }

    /** 复制为新版本 */
    @Operation(summary = "复制工艺路线新版本")
    @RequirePerm("mrp:routing:add")
    @OperLog(module = "生产制造", operation = "复制工艺路线")
    @Idempotent
    @PostMapping("/{id}/copy")
    public Result<Long> copy(@PathVariable Long id, @Valid @RequestBody RoutingCopyForm form) {
        return Result.ok(routingService.copy(id, form));
    }

    /** 审核工艺路线（草稿→有效） */
    @Operation(summary = "审核工艺路线")
    @RequirePerm("mrp:routing:audit")
    @OperLog(module = "生产制造", operation = "审核工艺路线")
    @Idempotent
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        routingService.audit(id);
        return Result.ok();
    }

    /** 停用工艺路线（有效→停用） */
    @Operation(summary = "停用工艺路线")
    @RequirePerm("mrp:routing:audit")
    @OperLog(module = "生产制造", operation = "停用工艺路线")
    @Idempotent
    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        routingService.disable(id);
        return Result.ok();
    }
}
