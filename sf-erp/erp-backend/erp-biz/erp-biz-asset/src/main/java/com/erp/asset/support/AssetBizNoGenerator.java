package com.erp.asset.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 固定资产模块业务单号生成器（规范：模块前缀 + 流水）。
 * <p>
 * 前缀：ZC-资产编码 / PZ-凭证号。
 * 关键点：凭证号与 erp-finance 的 FinanceBizNoGenerator <b>共用同一 Redis 序列</b>
 * （key = erp:no:PZ:yyyyMMdd），跨模块凭证号全局唯一（fin_voucher.uk_voucher_no）；
 * 资产编码 ZC 用全局序列（key = erp:no:ZC:asset，不按天隔离 —— 资产编码为终身编号）。
 * Redis 不可用时降级毫秒时间戳流水（开发环境兜底，流水可能不连续但不阻塞业务）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetBizNoGenerator {

    /** 资产编码前缀（ZC + 4 位流水，如 ZC0001） */
    public static final String ASSET = "ZC";
    /** 凭证前缀（与 erp-finance 共用 PZ 序列） */
    public static final String VOUCHER = "PZ";

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成下一个资产编码（ZC + 4 位全局流水）。
     *
     * @return 形如 ZC0007 的编码
     */
    public String nextAssetCode() {
        return ASSET + "%04d".formatted(nextSeq("erp:no:ZC:asset", null));
    }

    /**
     * 生成下一个凭证号（PZ + yyyyMMdd + 4 位流水；与 finance 模块共用序列）。
     *
     * @return 形如 PZ202609050001 的凭证号
     */
    public String nextVoucherNo() {
        String date = LocalDate.now().format(DATE);
        return VOUCHER + date + "%04d".formatted(nextSeq("erp:no:PZ:" + date, Duration.ofDays(2)));
    }

    /**
     * Redis INCR 取流水；key 不存在时从 1 开始。
     *
     * @param key      Redis key
     * @param expire   过期时间（null 表示不过期 —— 资产编码为全局终身序列）
     * @return 流水号（&ge;1）
     */
    private long nextSeq(String key, Duration expire) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value == null) {
                return fallbackSeq();
            }
            if (expire != null) {
                redisTemplate.expire(key, expire);
            }
            return value;
        } catch (RedisConnectionFailureException | IllegalStateException e) {
            log.warn("Redis 不可用，单号[{}]降级为时间戳流水", key, e);
            return fallbackSeq();
        }
    }

    /** Redis 不可用时的兜底流水（毫秒时间戳取模 4 位） */
    private long fallbackSeq() {
        return System.currentTimeMillis() % 10000;
    }
}
