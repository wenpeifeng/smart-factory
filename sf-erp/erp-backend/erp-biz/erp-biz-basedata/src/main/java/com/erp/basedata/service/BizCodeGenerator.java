package com.erp.basedata.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 基础资料编码生成器（Redis INCR 实现）。
 * <p>
 * 规则：{@code 前缀 + yyyyMMdd + 3位流水}，如 WL20260905001。
 * <ul>
 *   <li>Redis key 按天隔离（erp:seq:{prefix}:{yyyyMMdd}），天然按日归零；</li>
 *   <li>key 首次创建时设置 2 天过期，防止异常堆积；</li>
 *   <li>3 位流水超 999 后自然进位为 4 位（%03d 仅补零不截断），不影响唯一性。</li>
 * </ul>
 * 前缀常量：WL-物料 / KH-客户 / GYS-供应商 / CK-仓库。
 */
@Component
@RequiredArgsConstructor
public class BizCodeGenerator {

    /** 物料编码前缀 */
    public static final String PREFIX_MATERIAL = "WL";
    /** 客户编码前缀 */
    public static final String PREFIX_CUSTOMER = "KH";
    /** 供应商编码前缀 */
    public static final String PREFIX_SUPPLIER = "GYS";
    /** 仓库编码前缀 */
    public static final String PREFIX_WAREHOUSE = "CK";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成下一个业务编码。
     *
     * @param prefix 业务前缀（用本类 PREFIX_* 常量）
     * @return 形如 WL20260905001 的编码
     */
    public String nextCode(String prefix) {
        String date = LocalDate.now().format(DATE_FMT);
        String key = "erp:seq:" + prefix + ":" + date;
        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq == null) {
            // Redis 不可用属基础设施故障，直接快速失败，禁止降级为本地生成（会重号）
            throw new IllegalStateException("Redis INCR 失败，无法生成编码: " + key);
        }
        if (seq == 1L) {
            redisTemplate.expire(key, Duration.ofDays(2));
        }
        return prefix + date + String.format("%03d", seq);
    }
}
