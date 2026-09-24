package com.mes.kanban.controller;

import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.kanban.dto.form.AndonForm;
import com.mes.kanban.dto.vo.AndonVO;
import com.mes.kanban.service.AndonService;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 安灯接口（呼叫 + 闭环处理 + 看板查询）。
 */
@Tag(name = "看板-安灯")
@RestController
@RequestMapping("/kanban/andons")
@RequiredArgsConstructor
public class AndonController {

    private final AndonService andonService;

    /**
     * 发起安灯呼叫。
     */
    @Operation(summary = "发起安灯呼叫")
    @RequirePerm("kanban:andon:call")
    @OperLog(module = "安灯", operation = "发起安灯呼叫")
    @Idempotent
    @PostMapping
    public Result<Long> call(@Valid @RequestBody AndonForm form) {
        return Result.ok(andonService.call(form));
    }

    /**
     * 响应呼叫。
     */
    @Operation(summary = "响应呼叫")
    @RequirePerm("kanban:andon:respond")
    @OperLog(module = "安灯", operation = "响应呼叫")
    @Idempotent
    @PutMapping("/{id}/respond")
    public Result<Void> respond(@PathVariable Long id, @RequestParam String responder) {
        andonService.respond(id, responder);
        return Result.ok();
    }

    /**
     * 关闭呼叫。
     */
    @Operation(summary = "关闭呼叫")
    @RequirePerm("kanban:andon:close")
    @OperLog(module = "安灯", operation = "关闭呼叫")
    @Idempotent
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestParam(required = false) String closeRemark) {
        andonService.close(id, closeRemark);
        return Result.ok();
    }

    /**
     * 分页查询（看板轮询 OPEN/RESPOND，管理页看全部）。
     */
    @Operation(summary = "分页查询安灯")
    @RequirePerm("kanban:andon:list")
    @GetMapping
    public Result<PageResult<AndonVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String type) {
        return Result.ok(andonService.page(pageNum, pageSize, status, type));
    }
}
