package com.erp.basedata.config;

import com.erp.basedata.service.PurSupplierScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 供应商评分扫描器（V141）。
 * <p>
 * 每月 1 日 01:40 计算上一自然月评分；若上月为季末月（3/6/9/12）同时计算上一季度评分。
 * 重算=先删后插（服务内事务），天然幂等，单轮异常吞掉下月重试（亦可经
 * POST /purchase/supplier-scores/recalc 手工补算）。
 * 开关 supplier.score.enabled（默认开）。
 * <p>
 * 说明：@EnableScheduling 已由本模块 PriceCollectScheduleConfig（V131）开启，
 * 此处不重复标注，避免重复初始化。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SupplierScoreScheduleConfig {

    private final PurSupplierScoreService scoreService;

    /** 扫描器开关：supplier.score.enabled，默认开启 */
    @Value("${supplier.score.enabled:true}")
    private boolean enabled;

    /** 每月 1 日 01:40（错开 V131 价格扫描器与其它整点任务） */
    @Scheduled(cron = "0 40 1 1 * *")
    public void calcMonthlyAndQuarterly() {
        if (!enabled) {
            log.debug("[供应商评分][扫描器] 已关闭（supplier.score.enabled=false），跳过");
            return;
        }
        try {
            int monthCount = scoreService.recalc("MONTH", null);
            log.info("[供应商评分][扫描器] 上月评分完成，参与供应商 {} 家", monthCount);
        } catch (Exception e) {
            log.error("[供应商评分][扫描器] 上月评分失败(可手工补算): {}", e.getMessage(), e);
        }
        // 上月是季末月（3/6/9/12）时补算上一季度
        int lastMonth = java.time.LocalDate.now().minusMonths(1).getMonthValue();
        if (lastMonth % 3 == 0) {
            try {
                int quarterCount = scoreService.recalc("QUARTER", null);
                log.info("[供应商评分][扫描器] 上季度评分完成，参与供应商 {} 家", quarterCount);
            } catch (Exception e) {
                log.error("[供应商评分][扫描器] 上季度评分失败(可手工补算): {}", e.getMessage(), e);
            }
        }
    }
}
