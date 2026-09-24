package com.mes.integration.schedule;

import com.mes.integration.service.impl.ErpPushServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ERP 回传失败重试定时任务：每 5 分钟捞取 FAILED 且未超重试上限的推送记录重推。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpPushRetryJob {

    private final ErpPushServiceImpl pushService;

    /**
     * 重试入口（fixedDelay 保证上一轮跑完后再计时，避免重叠）。
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void retry() {
        int count = pushService.retryFailed();
        if (count > 0) {
            log.info("[ERP-PUSH] 重试完成，本轮处理 {} 条", count);
        }
    }
}
