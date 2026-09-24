package com.erp.integration.listener;

import com.erp.basedata.entity.BaseMaterial;
import com.erp.basedata.entity.BaseWarehouse;
import com.erp.basedata.mapper.BaseMaterialMapper;
import com.erp.basedata.mapper.BaseWarehouseMapper;
import com.erp.integration.dto.MesProductionOrderDTO;
import com.erp.integration.service.OutboxService;
import com.erp.mrp.entity.MrpWorkOrder;
import com.erp.mrp.event.WorkOrderReleasedEvent;
import com.erp.mrp.mapper.MrpWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 生产订单下达事件监听（契约接口 #2：生产订单下推）。
 * <p>AFTER_COMMIT：下达事务提交成功才下推，避免回滚后 MES 收到幽灵订单；
 * 下推失败仅落 outbox 退避重试，不影响下达主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderReleasedListener {

    private final MrpWorkOrderMapper workOrderMapper;
    private final BaseMaterialMapper materialMapper;
    private final BaseWarehouseMapper warehouseMapper;
    private final OutboxService outboxService;

    /**
     * 下达事件处理：重组装订单报文（orderNo 幂等键）入 outbox 并立即投递。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReleased(WorkOrderReleasedEvent event) {
        MrpWorkOrder wo = workOrderMapper.selectById(event.workOrderId());
        if (wo == null) {
            log.warn("[MES-PUSH] 下达事件订单不存在，跳过: id={}", event.workOrderId());
            return;
        }
        BaseMaterial material = materialMapper.selectById(wo.getMaterialId());
        BaseWarehouse warehouse = wo.getWarehouseId() == null ? null
                : warehouseMapper.selectById(wo.getWarehouseId());
        MesProductionOrderDTO dto = new MesProductionOrderDTO();
        dto.setOrderNo(wo.getOrderNo());
        dto.setMaterialCode(material == null ? null : material.getCode());
        dto.setMaterialName(material == null ? null : material.getName());
        dto.setQty(wo.getQty());
        dto.setBomVersion(wo.getBomVersion());
        // 工艺路线编码：ERP 工单未快照路线，置空由 MES 按物料默认路线取
        dto.setWarehouseCode(warehouse == null ? null : warehouse.getCode());
        dto.setPlanStartDate(wo.getPlanStartDate());
        dto.setPlanEndDate(wo.getPlanEndDate());
        dto.setRemark(wo.getRemark());
        outboxService.enqueue(OutboxService.BIZ_PRODUCTION_ORDER, wo.getId(), dto);
    }
}
