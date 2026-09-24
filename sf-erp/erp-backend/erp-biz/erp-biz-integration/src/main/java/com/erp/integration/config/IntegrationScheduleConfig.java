package com.erp.integration.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 集成模块定时任务开关（outbox 重试 / 主数据对账推送）。
 * 集中在本模块开启，避免启动类膨胀（与 MES 侧 IntegrationScheduleConfig 同款做法）。
 */
@Configuration
@EnableScheduling
public class IntegrationScheduleConfig {
}
