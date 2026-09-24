package com.erp.org.support;

import com.erp.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 组织管理模块单号生成器 —— 前缀 + yyyyMMdd + 3位流水（Redis 自增保证同日不重号）。
 * <p>
 * 前缀：FC 工厂 / IT 组织间调拨。例：IT20260905001。
 * Redis Key 按天隔离，次日自动归 1；超过 999 时自然增长为 4 位（唯一性优先于长度）。
 */
@Component
@RequiredArgsConstructor
public class OrgBizNoGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成下一个业务单号。
     *
     * @param prefix 单号前缀（FC/IT）
     * @return 形如 IT20260905001 的单号
     * @throws BusinessException Redis 不可用时抛出（单号唯一性优先，宁可失败不可重复）
     */
    public String next(String prefix) {
        String date = LocalDate.now().format(DATE_FMT);
        String key = "erp:seq:" + prefix + ":" + date;
        Long seq;
        try {
            seq = redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            throw BusinessException.ofKey("erp.org.doc-no-gen-failed", e.getMessage());
        }
        if (seq == null || seq <= 0) {
            throw BusinessException.ofKey("erp.inv.doc-no-gen-redis-error");
        }
        return prefix + date + "%03d".formatted(seq);
    }
}
