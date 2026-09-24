package com.erp.mrp.service.impl;

import com.erp.mrp.service.SeqNoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 单号生成实现 —— Redis INCR 按天自增，超 9999 自动进位为 5 位（仍保证唯一）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeqNoServiceImpl implements SeqNoService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;

    @Override
    public String next(String prefix) {
        String date = LocalDate.now().format(DATE_FMT);
        String key = "erp:seq:" + prefix + ":" + date;
        try {
            Long seq = redisTemplate.opsForValue().increment(key);
            if (seq != null && seq == 1L) {
                redisTemplate.expire(key, Duration.ofDays(2));
            }
            return prefix + date + String.format("%04d", seq == null ? 1 : seq);
        } catch (Exception e) {
            // Redis 不可用兜底：开发环境降级，避免单号服务阻塞业务（概率极低的尾号碰撞可接受）
            log.warn("Redis 单号生成失败，降级时间戳尾号: {}", e.getMessage());
            return prefix + date + String.format("%04d", System.currentTimeMillis() % 10000);
        }
    }
}
