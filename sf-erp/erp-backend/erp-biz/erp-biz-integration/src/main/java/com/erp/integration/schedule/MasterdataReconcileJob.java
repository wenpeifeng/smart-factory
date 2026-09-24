package com.erp.integration.schedule;

import com.erp.integration.config.IntegrationProperties;
import com.erp.integration.service.MasterdataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 主数据每日全量对账推送任务（契约接口 #1：主数据同步的"每日全量对账"腿）。
 * <p>MES 侧按幂等键 upsert，全量重推即对账补偿，差异随覆盖收敛。
 * cron 可配（erp.integration.mes.masterdata-reconcile-cron，默认每日 02:00）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterdataReconcileJob {

    private final MasterdataSyncService masterdataSyncService;
    private final IntegrationProperties properties;

    /**
     * 对账推送入口。
     */
    @Scheduled(cron = "${erp.integration.mes.masterdata-reconcile-cron:0 0 2 * * ?}")
    public void reconcile() {
        if (!properties.getMes().isMasterdataReconcileEnabled()) {
            return;
        }
        String summary = masterdataSyncService.reconcileAll();
        log.info("[MES-RECONCILE] 定时全量对账完成: {}", summary);
    }
}
