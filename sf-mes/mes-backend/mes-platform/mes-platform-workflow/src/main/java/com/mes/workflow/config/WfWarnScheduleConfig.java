package com.mes.workflow.config;

import com.mes.workflow.service.WfWarnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 预警定时任务配置。
 * <p>
 * 每小时整点全量扫描一次（库存安全线/客户信用/生产超期），
 * 开关 wf.warn.enabled（默认 true）—— 配置文件无需显式声明，代码内取默认值。
 * 说明：@EnableScheduling 挂在本模块配置类上，mes-server 无需改动
 * （组件扫描 com.mes.** 自动装配，符合"公共文件由主线程维护"的并行约定）。
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class WfWarnScheduleConfig {

    private final WfWarnService warnService;

    /** 预警扫描开关：wf.warn.enabled，默认开启 */
    @Value("${wf.warn.enabled:true}")
    private boolean warnEnabled;

    /**
     * 每小时整点执行预警扫描。
     * cron="0 0 * * * *" → 每小时第 0 分钟；异常在 scanOnce 内部吞掉并记日志，
     * 不中断调度线程。
     */
    @Scheduled(cron = "0 0 * * * *")
    public void scanWarn() {
        if (!warnEnabled) {
            log.debug("预警扫描已关闭（wf.warn.enabled=false），跳过");
            return;
        }
        int sent = warnService.scanOnce();
        if (sent > 0) {
            log.info("预警扫描完成，新发预警消息 {} 条", sent);
        }
    }
}
