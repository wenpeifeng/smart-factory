package com.erp.mrp.event;

/**
 * 生产订单下达完成事件（V1231 ERP-MES 集成）。
 * <p>发布点：{@code MrpWorkOrderServiceImpl.doRelease}（状态置 RELEASED、
 * 工序任务生成之后）。监听方：erp-biz-integration 的
 * {@code WorkOrderReleasedListener}（AFTER_COMMIT 后组装报文入 outbox 下推 MES）。
 * <p>放 mrp 模块而非 integration 的原因：事件类必须在发布方 classpath 上，
 * 依赖方向只能 integration -> mrp。
 *
 * @param workOrderId 生产订单主键（mrp_work_order.id）
 */
public record WorkOrderReleasedEvent(Long workOrderId) {
}
