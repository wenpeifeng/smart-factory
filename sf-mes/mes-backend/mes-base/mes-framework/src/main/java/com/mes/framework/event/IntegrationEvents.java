package com.mes.framework.event;

import java.math.BigDecimal;

/**
 * 集成事件契约（多模块共享，放 framework 防依赖环）。
 */
public final class IntegrationEvents {

    private IntegrationEvents() {
    }

    /**
     * 工单状态变更事件（plan 发布，integration 监听推送 ERP 工单进度镜像）。
     */
    public record WoStatusChanged(
            /** 工单 id */
            Long workorderId,
            /** MES 工单号 */
            String woNo,
            /** ERP 生产订单号 */
            String erpOrderNo,
            /** MES 状态（DRAFT/RELEASED/PRODUCING/FINISHED/CLOSED） */
            String mesStatus,
            /** 累计合格数 */
            BigDecimal goodQty,
            /** 累计报废数 */
            BigDecimal scrapQty) {
    }
}
