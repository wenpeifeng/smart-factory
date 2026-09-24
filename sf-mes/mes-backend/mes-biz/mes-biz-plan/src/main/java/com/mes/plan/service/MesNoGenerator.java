package com.mes.plan.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * MES 业务单号生成器（Redis INCR，前缀 + yyyyMMdd + 4位流水）。
 * <p>区别于 basedata 的 BizCodeGenerator（3 位流水）：工单/过站/齐套等
 * 单据量级更大，按 DDL 约定统一 4 位流水（%04d 仅补零不截断，超 9999 自然进位）。
 * 前缀：MO 工单 / QT 齐套 / GZ 过站 / LL 领料 / TL 退料 / JC 检验 / BH 不合格处置 /
 * FG 返工 / BF 报废。
 * <p>放在 plan 模块供 execution/material/quality 复用（依赖方向：execution/material/quality → plan）。
 */
@Component
@RequiredArgsConstructor
public class MesNoGenerator {

    /** 工单号前缀 */
    public static final String PREFIX_WORKORDER = "MO";
    /** 齐套分析单号前缀 */
    public static final String PREFIX_KIT_CHECK = "QT";
    /** 过站记录号前缀 */
    public static final String PREFIX_STATION = "GZ";
    /** 领料单号前缀 */
    public static final String PREFIX_PICKING = "LL";
    /** 退料单号前缀 */
    public static final String PREFIX_RETURN = "TL";
    /** 检验单号前缀 */
    public static final String PREFIX_INSPECT = "JC";
    /** 不合格处置单号前缀 */
    public static final String PREFIX_REJECT = "BH";
    /** 返工单号前缀 */
    public static final String PREFIX_REWORK = "FG";
    /** 报废单号前缀 */
    public static final String PREFIX_SCRAP = "BF";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成下一个单号（前缀 + yyyyMMdd + 4 位流水）。
     *
     * @param prefix 业务前缀（用本类 PREFIX_* 常量）
     * @return 形如 MO202609060001 的单号
     */
    public String next(String prefix) {
        String date = LocalDate.now().format(DATE_FMT);
        String key = "mes:seq:" + prefix + ":" + date;
        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq == null) {
            // Redis 不可用属基础设施故障，快速失败防重号
            throw new IllegalStateException("Redis INCR 失败，无法生成单号: " + key);
        }
        if (seq == 1L) {
            redisTemplate.expire(key, Duration.ofDays(2));
        }
        return prefix + date + String.format("%04d", seq);
    }
}
