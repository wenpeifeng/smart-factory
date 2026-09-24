package com.erp.system.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.system.dto.log.LoginLogQuery;
import com.erp.system.dto.log.LoginLogVO;
import com.erp.system.service.SysLoginLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志查询接口（写入由登录事件监听器自动完成，见 LoginLogListener）。
 * <p>
 * 与 /system/logs（操作日志）并列：操作日志记"登录后做了什么"，登录日志记"谁在何时从哪里登录/登出、成败与原因"。
 */
@Tag(name = "登录日志")
@RestController
@RequestMapping("/system/login-logs")
@RequiredArgsConstructor
public class SysLoginLogController {

    private final SysLoginLogService loginLogService;

    /** 分页查询登录日志 */
    @Operation(summary = "分页查询登录日志")
    @GetMapping
    public Result<PageResult<LoginLogVO>> page(@ModelAttribute LoginLogQuery query) {
        return Result.ok(loginLogService.page(query));
    }
}
