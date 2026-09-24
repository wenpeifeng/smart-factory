package com.erp.hr.service;

/**
 * 单号生成服务 —— 规则：前缀 + yyyyMMdd + 4位流水（员工工号 EMP…）。
 * <p>
 * 流水取 Redis 自增（key 按天隔离，并发不重号）；Redis 不可用时降级为
 * 时间戳尾号（仅开发环境兜底，会记 WARN 日志）。实现与 erp-mrp 同型，
 * 因业务模块间禁止横向依赖而在本模块内独立维护。
 */
public interface SeqNoService {

    /**
     * 生成下一个业务单号。
     *
     * @param prefix 单号前缀（EMP 等，见 HrConstants）
     * @return 形如 EMP202609050001 的单号
     */
    String next(String prefix);
}
