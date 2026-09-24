package com.mes.integration.schedule;

import com.mes.integration.authcenter.dto.UserSyncResultVO;
import com.mes.integration.service.AuthUserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * auth-center 用户对账定时任务：每小时全量同步一次。
 * <p>为什么全量而非 updatedSince 增量：中心侧离职/全局停用只改用户表、
 * 不触碰分配表 update_time，增量会漏（见其 IdmUserServiceImpl#leave）；
 * 全量对账在几千人规模亚秒完成，且是唯一能捕获档案侧变更的方式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthUserSyncJob {

    private final AuthUserSyncService userSyncService;

    /**
     * 每小时对账（fixedDelay：上一轮跑完再计时；失败不中断，下轮重试）。
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 300_000)
    public void reconcile() {
        try {
            UserSyncResultVO result = userSyncService.sync();
            log.info("[AUTH-SYNC] 定时对账完成: fetched={} inserted={} updated={} disabled={}",
                    result.getFetched(), result.getInserted(), result.getUpdated(), result.getDisabled());
        } catch (Exception ex) {
            log.warn("[AUTH-SYNC] 定时对账失败（下轮重试）: {}", ex.getMessage());
        }
    }
}
