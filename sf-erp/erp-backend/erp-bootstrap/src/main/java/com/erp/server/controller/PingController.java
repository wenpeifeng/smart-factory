package com.erp.server.controller;

import com.erp.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 健康检查接口 —— 供启动脚本 / 网关 / 运维探活使用。
 * 刻意不依赖任何业务模块。
 */
@Tag(name = "运维")
@RestController
public class PingController {

    /** 探活：返回服务名与当前时间，验证整个请求链路（网关->安全->MVC->JSON）可用 */
    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        return Result.ok(Map.of(
                "service", "erp-backend",
                "time", LocalDateTime.now().toString()));
    }
}
