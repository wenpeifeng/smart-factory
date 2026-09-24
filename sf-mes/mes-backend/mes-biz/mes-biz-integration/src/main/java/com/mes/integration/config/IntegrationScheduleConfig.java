package com.mes.integration.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 集成模块定时任务开关（回传重试等）。集中在本模块开启，避免启动类膨胀。
 */
@Configuration
@EnableScheduling
public class IntegrationScheduleConfig {
}
