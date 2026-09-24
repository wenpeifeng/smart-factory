package com.erp.integration.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.integration.entity.IntMessageLog;
import com.erp.integration.entity.IntOutbox;
import com.erp.integration.service.MasterdataSyncService;
import com.erp.integration.service.MesInboundService;
import com.erp.integration.service.OutboxService;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集成管理端接口：outbox 监控/手动重试 + 入站日志 + 主数据手动对账推送。
 * <p>前端「集成监控」页（outbox + 入站日志两 Tab）的数据源。
 */
@Tag(name = "集成-监控管理")
@RestController
@RequestMapping("/integration")
@RequiredArgsConstructor
public class IntegrationAdminController {

    private final OutboxService outboxService;
    private final MesInboundService mesInboundService;
    private final MasterdataSyncService masterdataSyncService;

    /** IPage -> PageResult（与 PageUtils.of 同口径的出参转换） */
    private static <E> PageResult<E> toPageResult(com.baomidou.mybatisplus.core.metadata.IPage<E> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 出站发件箱分页（状态/业务类型筛选）。
     */
    @Operation(summary = "outbox分页查询")
    @RequirePerm("integration:outbox:list")
    @GetMapping("/outbox")
    public Result<PageResult<IntOutbox>> outboxPage(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bizType) {
        return Result.ok(toPageResult(outboxService.page(pageNum, pageSize, status, bizType)));
    }

    /**
     * 手动重试单条 outbox（PENDING/DEAD 均可）。
     */
    @Operation(summary = "outbox手动重试")
    @RequirePerm("integration:outbox:retry")
    @OperLog(module = "系统集成", operation = "outbox手动重试")
    @PostMapping("/outbox/{id}/retry")
    public Result<Void> retry(@PathVariable Long id) {
        outboxService.manualRetry(id);
        return Result.ok();
    }

    /**
     * 入站消息日志分页（类型/状态筛选，含 DUPLICATE 标记）。
     */
    @Operation(summary = "入站日志分页查询")
    @RequirePerm("integration:log:list")
    @GetMapping("/inbound-logs")
    public Result<PageResult<IntMessageLog>> inboundLogPage(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status) {
        return Result.ok(toPageResult(mesInboundService.pageLogs(pageNum, pageSize, bizType, status)));
    }

    /**
     * 主数据手动全量对账推送（物料/BOM/工艺路线入 outbox）。
     */
    @Operation(summary = "主数据手动对账推送")
    @RequirePerm("integration:masterdata:push")
    @OperLog(module = "系统集成", operation = "主数据手动对账推送")
    @PostMapping("/masterdata/push")
    public Result<String> pushMasterdata() {
        return Result.ok(masterdataSyncService.reconcileAll());
    }
}
