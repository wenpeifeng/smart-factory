package com.erp.integration.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.integration.entity.IntOutbox;

/**
 * 出站发件箱服务：入队 + 立即投递 + 指数退避重试 + 死信管理。
 * <p>使用方（同 JVM 单体，直接注入）：
 * <ul>
 *   <li>生产订单下达监听器（WorkOrderReleasedListener）；</li>
 *   <li>主数据对账任务（MasterdataSyncService：物料/BOM/工艺路线全量推送）；</li>
 *   <li>重试 Job（OutboxRetryJob）与管理端手动重试。</li>
 * </ul>
 * stub 模式下客户端永远成功，outbox 直接落 SUCCESS（报文留痕 mock）。
 */
public interface OutboxService {

    /** 生产订单下推 */
    String BIZ_PRODUCTION_ORDER = "PRODUCTION_ORDER";
    /** 物料主数据下推 */
    String BIZ_MATERIAL = "MATERIAL";
    /** BOM 下推 */
    String BIZ_BOM = "BOM";
    /** 工艺路线下推 */
    String BIZ_ROUTING = "ROUTING";

    /**
     * 入队并立即尝试投递一次（失败不抛异常，转入退避重试）。
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @param payload 报文对象（JSON 序列化快照，重试时按 bizType 反序列化重发）
     */
    void enqueue(String bizType, Long bizId, Object payload);

    /**
     * 重试到期记录（定时任务调用）。
     *
     * @return 本轮实际处理的记录数
     */
    int retryDue();

    /**
     * 手动重试单条（管理端；PENDING/DEAD 均可，成功后置 SUCCESS 并清零退避）。
     *
     * @param id outbox 主键
     */
    void manualRetry(Long id);

    /**
     * 分页查询 outbox。
     *
     * @param pageNum  页码（从 1 起）
     * @param pageSize 页大小
     * @param status   状态筛选（可空）
     * @param bizType  业务类型筛选（可空）
     */
    IPage<IntOutbox> page(long pageNum, long pageSize, String status, String bizType);
}
