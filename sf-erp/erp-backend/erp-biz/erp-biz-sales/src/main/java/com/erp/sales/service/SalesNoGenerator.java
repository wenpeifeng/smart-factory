package com.erp.sales.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.LongSupplier;

/**
 * 销售模块单号生成器：模块前缀 + yyyyMMdd + 4位流水。
 * <p>
 * 主路径 Redis INCR（key 按天隔离，高并发不重号，当日 key 两天后过期）；
 * Redis 不可用时降级为"当日已有单据数 + 1"（调用方传入计数器），
 * 降级路径存在极小概率撞号（如草稿被逻辑删除后号码复用），
 * 单号列有唯一索引兜底，撞号时插入失败事务回滚。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesNoGenerator {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.BASIC_ISO_DATE;

    /** Redis key 前缀（erp:seq:{单据前缀}:{yyyyMMdd}） */
    private static final String SEQ_KEY = "erp:seq:%s:%s";

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成单号。
     *
     * @param prefix         单据前缀（SO 订单 / XS 出库 / RT 退货）
     * @param fallbackCount 降级计数器：返回"当日已占用号段数量"（Redis 故障时用）
     * @return 形如 SO202609050001 的单号（流水超过 9999 位时自然扩位）
     */
    public String next(String prefix, LongSupplier fallbackCount) {
        String date = LocalDate.now().format(DATE_PART);
        String key = SEQ_KEY.formatted(prefix, date);
        try {
            Long seq = redisTemplate.opsForValue().increment(key);
            if (seq != null && seq == 1L) {
                redisTemplate.expire(key, Duration.ofDays(2));
            }
            return prefix + date + "%04d".formatted(seq);
        } catch (Exception e) {
            long seq = fallbackCount.getAsLong() + 1;
            log.warn("Redis 单号流水不可用，降级为计数模式: prefix={}, seq={}", prefix, seq, e);
            return prefix + date + "%04d".formatted(seq);
        }
    }
}
