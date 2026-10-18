package com.mes.integration.controller;

import com.mes.common.result.Result;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.integration.authcenter.AuthCenterClient;
import com.mes.integration.authcenter.dto.UserSyncResultVO;
import com.mes.integration.service.AuthUserSyncService;
import com.mes.integration.config.IntegrationProperties;
import com.mes.framework.event.IdentityChangeEvent;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
    private final IntegrationProperties integrationProperties;

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

    /** 认证中心事务提交后的内部实时对账入口（令牌鉴权）。 */
    @PostMapping("/internal/sync")
    public ResponseEntity<Result<UserSyncResultVO>> internalSync(
            @RequestHeader(value = "X-Identity-Sync-Token", required = false) String token,
            @RequestParam(defaultValue = IdentityChangeEvent.SOURCE_GROUP) String source,
            @RequestParam(required = false) String reason) {
        if (!validSyncToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail(HttpStatus.UNAUTHORIZED.value(), "身份同步回调令牌无效"));
        }
        return ResponseEntity.ok(Result.ok(userSyncService.sync(source)));
    }

    private boolean validSyncToken(String actual) {
        String expected = integrationProperties.getAuthCenter().getSyncCallbackToken();
        return actual != null && expected != null && !expected.isBlank() && MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
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
        return Result.ok(userSyncService.sync(IdentityChangeEvent.SOURCE_LOCAL));
    }

    @Operation(summary = "维护认证中心员工基础档案")
    @RequirePerm("system:user:edit")
    @OperLog(module = "用户同步", operation = "维护员工基础档案")
    @PutMapping("/users/{employeeNo}/profile")
    public Result<UserSyncResultVO> updateProfile(@PathVariable String employeeNo,
                                                  @Valid @RequestBody ProfileForm form) {
        authCenterClient.updateProfile(employeeNo, new AuthCenterClient.ProfileForm(
                form.getRealName(), form.getPosition(), form.getEmail(), form.getPhone()));
        return Result.ok(userSyncService.sync(IdentityChangeEvent.SOURCE_LOCAL));
    }

    @Operation(summary = "重置认证中心统一账号密码")
    @RequirePerm("system:user:edit")
    @OperLog(module = "用户同步", operation = "重置统一账号密码")
    @PutMapping("/users/{employeeNo}/password")
    public Result<Void> resetPassword(@PathVariable String employeeNo,
                                      @Valid @RequestBody PasswordForm form) {
        authCenterClient.resetPassword(employeeNo, form.getPassword());
        return Result.ok();
    }

    @lombok.Data
    public static class ProfileForm {
        @jakarta.validation.constraints.NotBlank(message = "姓名不能为空")
        @jakarta.validation.constraints.Size(max = 64)
        private String realName;
        @jakarta.validation.constraints.Size(max = 64)
        private String position;
        @jakarta.validation.constraints.Size(max = 128)
        private String email;
        @jakarta.validation.constraints.Size(max = 32)
        private String phone;
    }

    @lombok.Data
    public static class PasswordForm {
        @jakarta.validation.constraints.NotBlank(message = "新密码不能为空")
        @jakarta.validation.constraints.Size(min = 6, max = 64, message = "密码长度须为 6-64 位")
        private String password;
    }
}
