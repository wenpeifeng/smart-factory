package com.erp.inventory.config;

import com.erp.inventory.service.WmsAbcService;
import com.erp.inventory.service.WmsTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * WMS 定时任务配置（V421）。
 * <p>
 * 1) 扫描器：每 30s 扫已审核未生成任务的出入库单 → PUTAWAY/PICK
 *    （开关 wms.auto-task.enabled 默认 true；回看窗口 wms.auto-task.days-back 默认 7 天）；
 * 2) ABC 循环盘点：每月 1 号 02:00 按分级频度自动生成盘点单（source=CYCLE），
 *    开关 wms.abc.cycle-enabled 默认 true。
 * <p>
 * 说明：@EnableScheduling 挂在本模块配置类上（与 workflow 模块 WfWarnScheduleConfig
 * 同一做法），erp-server 无需改动；组件扫描 com.erp.** 自动装配。
 * 调度线程无登录上下文时租户取默认 1000（OrgContextHolder 默认值），与库列默认一致。
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class WmsScheduleConfig {

    private final WmsTaskService taskService;
    private final WmsAbcService abcService;

    /** 扫描器开关：wms.auto-task.enabled，默认开启 */
    @Value("${wms.auto-task.enabled:true}")
    private boolean autoTaskEnabled;

    /** ABC 循环盘点开关：wms.abc.cycle-enabled，默认开启 */
    @Value("${wms.abc.cycle-enabled:true}")
    private boolean abcCycleEnabled;

    /**
     * 扫描器：每 30 秒一轮。fixedDelay 保证上一轮跑完再计时，
     * 单轮内部已吞异常（scanOnce 内 catch），不会中断调度线程。
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void scanTasks() {
        if (!autoTaskEnabled) {
            log.debug("[WMS][扫描器] 已关闭（wms.auto-task.enabled=false），跳过");
            return;
        }
        taskService.scanOnce();
    }

    /**
     * ABC 循环盘点：每月 1 号 02:00 触发（cron 秒 分 时 日 月）。
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void cycleChecks() {
        if (!abcCycleEnabled) {
            log.debug("[WMS][循环盘点] 已关闭（wms.abc.cycle-enabled=false），跳过");
            return;
        }
        abcService.generateCycleChecks();
    }
}
