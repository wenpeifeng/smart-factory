package com.erp.purchase.event;

/**
 * 采购入库单审核成功事件（V231，暂估链路挂点）。
 * <p>
 * {@code PurInstockServiceImpl#audit} 事务提交前发布，
 * 暂估服务以 {@code @TransactionalEventListener(AFTER_COMMIT)} 消费：
 * 暂估登记失败不影响已成功的入库审核（库存/应付/订单回写保持原子）。
 *
 * @param instockId 入库单 ID
 * @param instockNo 入库单号（RK...）
 */
public record PurInstockAuditedEvent(Long instockId, String instockNo) {
}
