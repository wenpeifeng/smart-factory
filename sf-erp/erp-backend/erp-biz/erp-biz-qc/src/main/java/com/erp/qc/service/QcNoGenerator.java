package com.erp.qc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 质量模块单据流水号生成器 —— Redis INCR + 日期前缀（与一期各模块同口径）。
 * <p>
 * 规则：{@code 前缀 + yyyyMMdd + 4位流水}，如 QC202609050001 / RJ202609050001。
 * key 按天隔离并设置 48h 过期；Redis 不可用直接失败（降级本地生成会重号）。
 */
@Component
@RequiredArgsConstructor
public class QcNoGenerator {

    /** 单号前缀：检验单 */
    public static final String PREFIX_INSPECT = "QC";
    /** 单号前缀：不合格品处置单 */
    public static final String PREFIX_REJECT = "RJ";

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成下一个单号。
     *
     * @param prefix 单据前缀（QC/RJ）
     * @return 形如 QC202609050001 的单号
     */
    public String next(String prefix) {
        String today = LocalDate.now().format(DAY_FMT);
        String key = "erp:no:" + prefix + ":" + today;
        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq == null) {
            throw new IllegalStateException("Redis INCR 失败，无法生成单号: " + key);
        }
        if (seq == 1L) {
            redisTemplate.expire(key, Duration.ofHours(48));
        }
        return prefix + today + "%04d".formatted(seq);
    }
}
