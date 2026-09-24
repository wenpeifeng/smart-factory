package com.erp.inventory.support;

import com.erp.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务单号生成器 —— 模块前缀 + yyyyMMdd + 4位流水（Redis 自增保证同日不重号）。
 * <p>
 * 例：OI202609050001（其他入库）/ OO...（其他出库）/ CK...（盘点）/ TR...（调拨）。
 * Redis Key 按天隔离，次日自动从 1 开始；Key 无过期也无妨（一天一个 Key，量极小）。
 */
@Component
@RequiredArgsConstructor
public class BizNoGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成下一个业务单号。
     *
     * @param prefix 单号前缀（OI/OO/CK/TR）
     * @return 形如 OI202609050001 的单号
     * @throws BusinessException Redis 不可用时抛出（单号是单据唯一键，宁可失败不可重复）
     */
    public String next(String prefix) {
        String date = LocalDate.now().format(DATE_FMT);
        String key = "erp:seq:" + prefix + ":" + date;
        Long seq;
        try {
            seq = redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            throw BusinessException.ofKey("erp.inv.doc-no-gen-redis-unavailable", e.getMessage());
        }
        if (seq == null || seq <= 0) {
            throw BusinessException.ofKey("erp.inv.doc-no-gen-redis-error");
        }
        // 超过 9999 时自然增长为 5 位，不截断（唯一性优先于长度）
        return prefix + date + "%04d".formatted(seq);
    }
}
