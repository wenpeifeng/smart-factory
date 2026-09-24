package com.mes.integration.controller;

import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.integration.authcenter.AuthCenterClient;
import com.mes.integration.authcenter.dto.UserSyncResultVO;
import com.mes.integration.service.AuthUserSyncService;
import com.mes.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * auth-center 用户同步接口（管理端手动触发；正式运行由 SF 同步任务/回调驱动）。
 */
@Tag(name = "集成-用户同步")
@RestController
@RequestMapping("/integration/auth")
@RequiredArgsConstructor
public class AuthSyncController {

    private final AuthUserSyncService userSyncService;
    private final AuthCenterClient authCenterClient;

    /**
     * 手动触发一次对账式用户同步。
     */
    @Operation(summary = "同步 auth-center 用户")
    @RequirePerm("integration:authsync:run")
    @OperLog(module = "用户同步", operation = "手动同步 auth-center 用户")
    @Idempotent
    @PostMapping("/sync")
    public Result<UserSyncResultVO> sync() {
        return Result.ok(userSyncService.sync());
    }

    /**
     * 应用级禁用/启用用户（MES 管理端入口：实际调认证中心，仅影响本应用；
     * 离职/全局停用由中心拒绝），完成后即时同步刷新本地副本。
     */
    @Operation(summary = "应用级启停用户")
    @RequirePerm("integration:authsync:run")
    @OperLog(module = "用户同步", operation = "应用级启停用户")
    @Idempotent
    @PutMapping("/users/{employeeNo}/status")
    public Result<UserSyncResultVO> changeAppStatus(@PathVariable String employeeNo,
                                                    @RequestParam boolean enabled) {
        if (enabled) {
            authCenterClient.enableUser(employeeNo);
        } else {
            authCenterClient.disableUser(employeeNo);
        }
        // 中心状态变更后拉一次同步，本地副本立即生效
        return Result.ok(userSyncService.sync());
    }
}
