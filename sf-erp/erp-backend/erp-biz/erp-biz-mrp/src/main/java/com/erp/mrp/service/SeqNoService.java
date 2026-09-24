package com.erp.mrp.service;

/**
 * 单号/批次号生成服务 —— 规则：前缀 + yyyyMMdd + 4位流水。
 * <p>
 * 流水取 Redis 自增（key 按天隔离，并发不重号）；Redis 不可用时降级为
 * 时间戳尾号（仅开发环境兜底，会记 WARN 日志）。
 */
public interface SeqNoService {

    /**
     * 生成下一个业务单号。
     *
     * @param prefix 单号前缀（MO/MI/OS/MR 等，见 MrpConstants）
     * @return 形如 MO202609050001 的单号
     */
    String next(String prefix);
}
