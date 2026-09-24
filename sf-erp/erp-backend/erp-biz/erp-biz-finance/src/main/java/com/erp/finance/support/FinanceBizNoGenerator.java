package com.erp.finance.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务单号生成器（规范：模块前缀 + yyyyMMdd + 4 位流水）。
 * <p>
 * 财务模块前缀：SK-收款单 / FK-付款单 / PZ-凭证。
 * 流水号用 Redis INCR（key 按天隔离，次日自然归零），高并发下不重号；
 * Redis 不可用时降级为毫秒时间戳取模，保证可用性（演示/开发环境兜底，
 * 流水可能不连续但不会阻塞业务）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanceBizNoGenerator {

    /** 收款单前缀 */
    public static final String RECEIPT = "SK";
    /** 付款单前缀 */
    public static final String PAYMENT = "FK";
    /** 凭证前缀 */
    public static final String VOUCHER = "PZ";

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成下一个业务单号。
     *
     * @param prefix 单号前缀（SK / FK / PZ）
     * @return 形如 SK202609050001 的单号
     */
    public String next(String prefix) {
        String date = LocalDate.now().format(DATE);
        String key = "erp:no:" + prefix + ":" + date;
        long seq;
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value == null) {
                seq = fallbackSeq();
            } else {
                // 当天 key 设置 2 天过期，避免残留
                redisTemplate.expire(key, Duration.ofDays(2));
                seq = value;
            }
        } catch (RedisConnectionFailureException | IllegalStateException e) {
            log.warn("Redis 不可用，单号[{}]降级为时间戳流水", prefix, e);
            seq = fallbackSeq();
        }
        return prefix + date + "%04d".formatted(seq);
    }

    /** Redis 不可用时的兜底流水（毫秒时间戳取模 4 位） */
    private long fallbackSeq() {
        return System.currentTimeMillis() % 10000;
    }
}
