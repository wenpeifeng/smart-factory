package com.erp.system.authcenter.schedule;

import com.erp.system.authcenter.dto.UserSyncResultVO;
import com.erp.system.authcenter.service.AuthUserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 认证中心用户同步定时任务（默认每小时整点，auth.center.sync-cron 可调）。
 * <p>@EnableScheduling 挂在本模块配置类上（与 inventory/workflow 模块同一做法）；
 * auth.center.sync-enabled=false 时整个任务不装配。同步失败仅记日志，
 * 下个周期自动重试（对账式，无状态）。
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth.center", name = "sync-enabled", havingValue = "true", matchIfMissing = true)
public class AuthUserSyncJob {

    private final AuthUserSyncService userSyncService;

    /** 每小时对账一次（cron 取配置，默认 0 0 * * * ?） */
    @Scheduled(cron = "${auth.center.sync-cron:0 0 * * * ?}")
    public void syncUsers() {
        try {
            UserSyncResultVO result = userSyncService.sync();
            log.info("[AUTH-SYNC] 定时同步完成: total={}, added={}, updated={}, disabled={}",
                    result.getTotal(), result.getAdded(), result.getUpdated(), result.getDisabled());
        } catch (Exception e) {
            log.error("[AUTH-SYNC] 定时同步失败（下个周期重试）: {}", e.getMessage(), e);
        }
    }
}
