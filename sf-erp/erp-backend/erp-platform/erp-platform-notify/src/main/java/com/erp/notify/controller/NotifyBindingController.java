package com.erp.notify.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.framework.security.RequirePerm;
import com.erp.notify.core.NotifyService;
import com.erp.notify.dto.BindingForm;
import com.erp.notify.dto.BindingQuery;
import com.erp.notify.dto.BindingVO;
import com.erp.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户外部账号绑定接口（钉钉/企微通道按手机号找人）。
 */
@Tag(name = "用户绑定")
@RestController
@RequestMapping("/notify/bindings")
@RequiredArgsConstructor
public class NotifyBindingController {

    private final NotifyService notifyService;

    /** 绑定分页 */
    @Operation(summary = "绑定分页")
    @RequirePerm("system:notify:list")
    @GetMapping
    public Result<PageResult<BindingVO>> page(@ModelAttribute BindingQuery query) {
        return Result.ok(notifyService.pageBindings(query));
    }

    /** 新增/覆盖绑定（同用户同通道唯一） */
    @Operation(summary = "新增绑定")
    @OperLog(module = "通知中心", operation = "新增用户绑定")
    @RequirePerm("system:notify:edit")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody BindingForm form) {
        return Result.ok(notifyService.createBinding(form));
    }

    /** 删除绑定 */
    @Operation(summary = "删除绑定")
    @OperLog(module = "通知中心", operation = "删除用户绑定")
    @RequirePerm("system:notify:edit")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notifyService.deleteBinding(id);
        return Result.ok();
    }
}
