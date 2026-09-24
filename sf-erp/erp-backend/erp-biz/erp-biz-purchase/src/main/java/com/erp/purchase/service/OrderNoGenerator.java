package com.erp.purchase.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 单据流水号生成器 —— Redis INCR + 日期前缀。
 * <p>
 * 规则：{@code 前缀 + yyyyMMdd + 4位流水}，如 PO202609050001 / RK202609050001 / RT202609050001。
 * Redis 自增保证同一进程/多实例高并发下不重号；key 按天隔离并设置 48h 过期，
 * 流水超 9999 时自然进位为 5 位（仅极端量级，接受）。
 */
@Component
@RequiredArgsConstructor
public class OrderNoGenerator {

    /** 单号前缀：采购订单 */
    public static final String PREFIX_PUR_ORDER = "PO";
    /** 单号前缀：采购入库单 */
    public static final String PREFIX_PUR_INSTOCK = "RK";
    /** 单号前缀：采购退货单 */
    public static final String PREFIX_PUR_RETURN = "RT";
    /** 单号前缀：采购暂估应付单（V231，3 位流水） */
    public static final String PREFIX_PUR_ESTIMATION = "ZG";

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成下一个单号（4 位流水，PO/RK/RT）。
     */
    public String next(String prefix) {
        return next(prefix, 4);
    }

    /**
     * 生成下一个单号（自定义流水宽度）。
     *
     * @param prefix 单据前缀（PO/RK/RT/ZG）
     * @param width  流水位数（如暂估单 ZG 为 3 位：ZG20260905001）
     */
    public String next(String prefix, int width) {
        String today = LocalDate.now().format(DAY_FMT);
        String key = "erp:no:" + prefix + ":" + today;
        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq == null) {
            // Redis 不可用属于系统级故障，直接失败交给全局异常处理，不能降级为本地生成（会重号）
            throw new IllegalStateException("Redis INCR 失败，无法生成单号: " + key);
        }
        if (seq == 1L) {
            // 当天第一个号：设置过期，避免 key 无限堆积
            redisTemplate.expire(key, java.time.Duration.ofHours(48));
        }
        return (prefix + today + "%0" + width + "d").formatted(seq);
    }
}
