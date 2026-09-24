package com.mes.quality.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 质检单号生成器（Redis INCR，前缀 + yyyyMMdd + 4位流水）。
 * <p>前缀：JC 检验单 / BH 不合格处置单。与 plan 模块的 MesNoGenerator 同规则，
 * 独立放置以避免 quality 对 plan 的额外依赖。
 */
@Component
@RequiredArgsConstructor
public class QcNoGenerator {

    /** 检验单前缀 */
    public static final String PREFIX_INSPECT = "JC";
    /** 不合格处置单前缀 */
    public static final String PREFIX_REJECT = "BH";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成下一个单号。
     *
     * @param prefix 业务前缀
     * @return 形如 JC202609060001 的单号
     */
    public String next(String prefix) {
        String date = LocalDate.now().format(DATE_FMT);
        String key = "mes:seq:" + prefix + ":" + date;
        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq == null) {
            throw new IllegalStateException("Redis INCR 失败，无法生成单号: " + key);
        }
        if (seq == 1L) {
            redisTemplate.expire(key, Duration.ofDays(2));
        }
        return prefix + date + String.format("%04d", seq);
    }
}
