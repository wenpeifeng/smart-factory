package com.erp.integration.schedule;

import com.erp.integration.config.IntegrationProperties;
import com.erp.integration.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 出站发件箱重试定时任务：每分钟捞取 PENDING 且退避到期的记录重投
 * （fixedDelay 保证上一轮跑完再计时，避免重叠；与 MES 侧 ErpPushRetryJob 同款做法）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRetryJob {

    private final OutboxService outboxService;
    private final IntegrationProperties properties;

    /**
     * 重试入口。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void retry() {
        if (!properties.getMes().isRetryEnabled()) {
            return;
        }
        int count = outboxService.retryDue();
        if (count > 0) {
            log.info("[OUTBOX] 重试完成，本轮处理 {} 条", count);
        }
    }
}
