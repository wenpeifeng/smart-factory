package com.erp.cost.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 成本模块单号生成器（规范：模块前缀 + yyyyMMdd + 4 位流水）。
 * <p>
 * 凭证号沿用 finance 模块 PZ 前缀与同一 Redis Key（erp:no:PZ:日期），
 * 与 finance 的 FinanceBizNoGenerator 共享流水空间，保证 fin_voucher.uk_voucher_no 不冲突。
 * Redis 不可用时降级为毫秒时间戳流水（可用性优先，uk 索引兜底防重）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CostBizNoGenerator {

    /** 凭证前缀（与 finance 模块一致，共享流水空间） */
    public static final String VOUCHER = "PZ";

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成下一个单号（PZ+yyyyMMdd+4位流水）。
     *
     * @param prefix 单号前缀（当前仅 PZ）
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
                redisTemplate.expire(key, Duration.ofDays(2));
                seq = value;
            }
        } catch (RedisConnectionFailureException | IllegalStateException e) {
            log.warn("Redis 不可用，单号[{}]降级为时间戳流水", prefix, e);
            seq = fallbackSeq();
        }
        return prefix + date + "%04d".formatted(seq);
    }

    /** 降级流水：当日 0 点以来的毫秒数（万位截取，仅极端降级场景使用） */
    private long fallbackSeq() {
        long millis = System.currentTimeMillis() % 86_400_000L;
        return Math.max(1, millis / 10);
    }
}
