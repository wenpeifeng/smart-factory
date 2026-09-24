package com.erp.system.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.system.dto.log.OperLogQuery;
import com.erp.system.dto.log.OperLogVO;
import com.erp.system.service.SysOperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.security.RequirePerm;

/**
 * 操作日志查询接口（写入由 @OperLog 切面自动完成）。
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/system/logs")
@RequiredArgsConstructor
public class SysOperLogController {

    private final SysOperLogService operLogService;

    /** 分页查询操作日志 */
    @Operation(summary = "分页查询操作日志")
    @RequirePerm("system:log:list")
    @GetMapping
    public Result<PageResult<OperLogVO>> page(@ModelAttribute OperLogQuery query) {
        return Result.ok(operLogService.page(query));
    }
}
