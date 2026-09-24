package com.mes.trace.service.impl;

import com.mes.plan.entity.PlanWorkorder;
import com.mes.plan.mapper.PlanWorkorderMapper;
import com.mes.trace.entity.TraceLink;
import com.mes.framework.event.TraceEvents;
import com.mes.trace.mapper.TraceLinkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * 追溯事件监听：把领料上料与过站关键件绑定落成 trace_link。
 * <p>AFTER_COMMIT：上游事务成功提交后才写追溯，失败不产生脏链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceListener {

    private final TraceLinkMapper traceLinkMapper;
    private final PlanWorkorderMapper workorderMapper;

    /**
     * 领料审核 → 批次上料链路（productNo 暂以工单号产出批次表达：WO:{woNo}）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPickingAudited(TraceEvents.PickingAudited event) {
        PlanWorkorder wo = workorderMapper.selectById(event.workorderId());
        if (wo == null) {
            return;
        }
        for (TraceEvents.PickingAudited.Item item : event.items()) {
            if (!StringUtils.hasText(item.batchNo())) {
                continue;
            }
            TraceLink link = new TraceLink();
            link.setProductNo("WO:" + wo.getWoNo());
            link.setWorkorderId(wo.getId());
            link.setWoStepId(null);
            link.setComponentNo(item.batchNo());
            link.setComponentType("BATCH");
            link.setMaterialId(item.materialId());
            link.setQty(item.qty());
            link.setLinkTime(event.auditTime());
            traceLinkMapper.insert(link);
        }
        log.info("[TRACE] 领料上料追溯: pickingId={} links={}", event.pickingId(), event.items().size());
    }

    /**
     * 过站报工 → 关键件 SN 绑定链路（产品 SN x 工序）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStationReported(TraceEvents.StationReported event) {
        if (!StringUtils.hasText(event.sn()) || event.keyComponentSns().isEmpty()) {
            return;
        }
        for (String keySn : event.keyComponentSns()) {
            TraceLink link = new TraceLink();
            link.setProductNo(event.sn());
            link.setWorkorderId(event.workorderId());
            link.setWoStepId(event.woStepId());
            link.setComponentNo(keySn);
            link.setComponentType("SN");
            link.setQty(java.math.BigDecimal.ONE);
            link.setLinkTime(event.stationTime());
            traceLinkMapper.insert(link);
        }
        log.info("[TRACE] 关键件绑定追溯: recordId={} links={}", event.recordId(), event.keyComponentSns().size());
    }
}
