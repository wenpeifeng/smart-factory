package com.erp.system.authcenter.schedule;

import com.erp.system.authcenter.dto.UserSyncResultVO;
import com.erp.system.authcenter.service.AuthUserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * 认证中心用户自动对账任务（默认每分钟，auth.center.sync-cron 可调）。
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

    /** 首次启动即对账，保证 clone 后首次运行也能看到认证中心已分配人员。 */
    @EventListener(ApplicationReadyEvent.class)
    public void initialSync() {
        runSync("启动");
    }

    /** 每分钟对账一次；页面无需人工触发。 */
    @Scheduled(cron = "${auth.center.sync-cron:0 * * * * ?}")
    public void syncUsers() {
        runSync("定时");
    }

    private void runSync(String trigger) {
        try {
            UserSyncResultVO result = userSyncService.sync();
            log.info("[AUTH-SYNC] {}同步完成: total={}, added={}, updated={}, disabled={}",
                    trigger, result.getTotal(), result.getAdded(), result.getUpdated(), result.getDisabled());
        } catch (Exception e) {
            log.error("[AUTH-SYNC] {}同步失败（下个周期重试）: {}", trigger, e.getMessage(), e);
        }
    }
}
