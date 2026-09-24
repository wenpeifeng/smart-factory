package com.erp.finance.config;

import com.erp.finance.context.BookContext;
import com.erp.finance.service.VoucherEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 凭证引擎定时扫描配置（V521，与 WMS 扫描器同一模式）。
 * <p>
 * 每 30s 扫描八类已生效（已审核/已开票已认证/已落库）且当前账套尚无对应凭证的
 * 单据，按凭证模板自动生成 DRAFT 凭证。零侵入业务模块：只读约定表
 * （VoucherEngineMapper），不 import 采购/销售/发票代码。
 * <ul>
 *   <li>开关 {@code biz.voucher.auto.enabled}（默认 true）；</li>
 *   <li>目标账套 {@code biz.voucher.auto.book-id}（默认 1001 主账套；
 *       业务单据本身无账套维度，凭证统一落主账套，凭证可按需切换）；</li>
 *   <li>回看窗口 {@code biz.voucher.auto.days-back}（默认 7 天，防存量单据一次性涌入；
 *       历史单据补录用 POST /finance/voucher-engine/scan?from=&to=）。</li>
 * </ul>
 * 说明：@EnableScheduling 挂本模块配置类（与 inventory 的 WmsScheduleConfig 同一做法），
 * erp-server 无需改动；调度线程无登录上下文时租户取默认 1000（OrgContextHolder 默认值）。
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class VoucherEngineScheduleConfig {

    private final VoucherEngineService engineService;

    /** 扫描器开关：biz.voucher.auto.enabled，默认开启 */
    @Value("${biz.voucher.auto.enabled:true}")
    private boolean autoEnabled;

    /** 目标账套：biz.voucher.auto.book-id，默认 1001 主账套 */
    @Value("${biz.voucher.auto.book-id:1001}")
    private long autoBookId;

    /** 回看窗口（天）：只扫近 N 天业务日期的单据 */
    @Value("${biz.voucher.auto.days-back:7}")
    private int daysBack;

    /**
     * 扫描器：每 30 秒一轮。fixedDelay 保证上一轮跑完再计时，
     * 单轮内部已吞异常（scan 内 per-doc catch），不会中断调度线程。
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void scanVouchers() {
        if (!autoEnabled) {
            log.debug("[凭证引擎][扫描器] 已关闭（biz.voucher.auto.enabled=false），跳过");
            return;
        }
        try {
            // 调度线程无请求上下文：显式设定目标账套（FinBookContext 默认 1001，此处可配置）
            BookContext.setBookId(autoBookId);
            engineService.scan(null, null, null, daysBack);
        } catch (Exception e) {
            // 兜底：调度线程不能死，下一轮重试
            log.error("[凭证引擎][扫描器] 扫描异常：{}", e.getMessage(), e);
        } finally {
            BookContext.clear();
        }
    }
}
