package com.erp.basedata.config;

import com.erp.basedata.service.PurPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 采购价格流水扫描器（V131）。
 * <p>
 * 每 30s 扫一轮「已审核未沉淀」的采购订单（10/20/30）、采购入库单（10）、
 * 定标 RFQ（10），沉淀为 pur_price_record —— 对采购模块零侵入
 * （只读约定表，不改其代码）。开关 price.auto-collect.enabled（默认开）。
 * <p>
 * 说明：@EnableScheduling 挂在本模块配置类上（与 workflow/inventory 模块同一做法）；
 * 调度线程无登录上下文时租户取默认 1000（OrgContextHolder 默认值），与库列默认一致。
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class PriceCollectScheduleConfig {

    private final PurPriceService purPriceService;

    /** 扫描器开关：price.auto-collect.enabled，默认开启 */
    @Value("${price.auto-collect.enabled:true}")
    private boolean enabled;

    /**
     * 每 30 秒一轮。fixedDelay 保证上一轮跑完再计时；
     * 单轮异常在此吞掉（下轮重试），不会中断调度线程。
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    public void collectPriceRecords() {
        if (!enabled) {
            log.debug("[价格库][扫描器] 已关闭（price.auto-collect.enabled=false），跳过");
            return;
        }
        try {
            purPriceService.collectOnce();
        } catch (Exception e) {
            log.error("[价格库][扫描器] 本轮沉淀失败(下轮重试): {}", e.getMessage(), e);
        }
    }
}
