package com.erp.invoice.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 发票号码生成器（增值税电子发票号码：8 位数字）。
 * <p>
 * 号段从 10000001 起（Redis 全局自增 key erp:no:INVNO），高并发不重号；
 * Redis 不可用时降级为随机 8 位号段 [10000000, 99999999]，
 * 兜底可用性（演示/开发环境；极端情况下由 uk_sinv_no / uk_pinv_no 唯一约束兜底报错）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceNoGenerator {

    /** 全局流水 key（发票号码不按天隔离，号段连续递增） */
    private static final String SEQ_KEY = "erp:no:INVNO";

    /** 号段基数：序列 1 → 10000001 */
    private static final long BASE = 10_000_000L;

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成下一个 8 位发票号码。
     *
     * @return 形如 10000001 的 8 位数字字符串
     */
    public String next() {
        long seq;
        try {
            Long value = redisTemplate.opsForValue().increment(SEQ_KEY);
            if (value == null) {
                seq = fallbackSeq();
            } else {
                // 流水 key 常驻不过期（发票号全局唯一），仅设一个较长兜底 TTL 防残留
                redisTemplate.expire(SEQ_KEY, Duration.ofDays(365));
                seq = value;
            }
        } catch (RedisConnectionFailureException | IllegalStateException e) {
            log.warn("Redis 不可用，发票号码降级为随机号段", e);
            seq = fallbackSeq();
        }
        long no = BASE + seq;
        if (no > 99_999_999L) {
            // 号段耗尽理论上不可能（近亿张），防御性循环复用随机段
            no = fallbackSeq() + BASE;
        }
        return String.valueOf(no);
    }

    /** Redis 不可用时的兜底流水（随机 0~89999999，叠加基数后落在 8 位号段内） */
    private long fallbackSeq() {
        return (long) (Math.random() * 89_999_999L);
    }
}
