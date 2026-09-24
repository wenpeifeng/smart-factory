package com.erp.purchase.config;

import com.erp.purchase.service.PurEstimationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 采购暂估（货到票未到）定时任务配置（V231）。
 * <p>
 * 扫描器每 60s 一轮，职责两件事（{@code PurEstimationService#scanOnce}）：
 * <ol>
 *   <li>补提暂估：已审核入库单 + 无暂估 + 无生效蓝字发票
 *       （instant 模式补全部漏单，month-end 模式只补已关账月份=月初补提）；</li>
 *   <li>票到自动冲回：已认证蓝字进项发票，其关联入库单存在未冲完暂估且该发票未冲回过。</li>
 * </ol>
 * 开关 purchase.estimation.enabled（默认 true）关闭时扫描器整体跳过；
 * 模式 purchase.estimation.mode：instant（默认，审核即暂估）/ month-end（月末汇总，月初补提）。
 * <p>
 * 说明：@EnableScheduling 挂本模块配置类（与 inventory WmsScheduleConfig / workflow
 * WfWarnScheduleConfig 同一做法），erp-server 无需改动。fixedDelay 保证上一轮跑完再计时，
 * 单轮内部逐单吞异常，不会中断调度线程。
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class PurEstimationScheduleConfig {

    private final PurEstimationService estimationService;

    /** 暂估开关：purchase.estimation.enabled，默认开启 */
    @Value("${purchase.estimation.enabled:true}")
    private boolean enabled;

    /** 扫描器：每 60 秒一轮（补提暂估 + 已认证发票自动冲回） */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scanEstimations() {
        if (!enabled) {
            log.debug("[暂估][扫描器] 已关闭（purchase.estimation.enabled=false），跳过");
            return;
        }
        try {
            estimationService.scanOnce();
        } catch (Exception ex) {
            // 兜底：scanOnce 内部已逐单吞异常，这里再防一层，保证调度线程不死
            log.error("[暂估][扫描器] 本轮执行异常：{}", ex.getMessage(), ex);
        }
    }
}
