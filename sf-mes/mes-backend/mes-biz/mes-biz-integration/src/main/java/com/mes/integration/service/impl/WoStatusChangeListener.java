package com.mes.integration.service.impl;

import com.mes.framework.event.IntegrationEvents;
import com.mes.integration.erp.dto.ErpOrderStatusDTO;
import com.mes.integration.service.ErpPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 工单状态变更监听：推送 ERP 工单进度镜像（mes_status 列）。
 * <p>AFTER_COMMIT + 异步：镜像推送失败不影响工单流转（推送日志重试兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WoStatusChangeListener {

    private final ErpPushService erpPushService;

    /**
     * 状态流转事件入口。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(IntegrationEvents.WoStatusChanged e) {
        try {
            ErpOrderStatusDTO dto = new ErpOrderStatusDTO();
            dto.setWoNo(e.woNo());
            dto.setErpOrderNo(e.erpOrderNo());
            dto.setMesStatus(e.mesStatus());
            dto.setGoodQty(e.goodQty());
            dto.setScrapQty(e.scrapQty());
            dto.setEventTime(LocalDateTime.now());
            erpPushService.pushOrderStatus(e.workorderId(), dto);
        } catch (Exception ex) {
            log.warn("[ERP-PUSH] 工单进度镜像推送失败（重试兜底）: woNo={} - {}", e.woNo(), ex.getMessage());
        }
    }
}
