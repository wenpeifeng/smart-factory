package com.mes.integration.controller;

import com.mes.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 回调预留端点（/sso/callback，安全白名单内）。
 * <p>前端回调路由落地页调用 {@code POST /integration/oidc/token} 完成换令牌；
 * 本端点保留给 auth-center 服务端重直连场景，直接透传授权码。
 */
@Tag(name = "集成-OIDC登录")
@RestController
public class SsoCallbackController {

    /**
     * 授权码回调（透传确认，实际换令牌走 /integration/oidc/token）。
     */
    @Operation(summary = "SSO授权码回调（预留）")
    @GetMapping("/sso/callback")
    public Result<String> callback(@RequestParam(required = false) String code,
                                   @RequestParam(required = false) String state) {
        return Result.ok("code=" + code + ", state=" + state
                + "；请使用 POST /integration/oidc/token 完成授权码换令牌");
    }
}
