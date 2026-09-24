package com.erp.purchase.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购入库单主表实体（pur_instock）。
 * <p>
 * 从已审核采购订单生成（选单），状态机：0草稿 -> 10已审核。
 * 审核动作在一个事务内完成三件事（见 PurInstockServiceImpl#audit）：
 * 写库存流水 inv_movement、增减库存 inv_stock（条件 UPDATE 防并发/负库存）、
 * 生成应付 fin_payable，并回写订单累计入库数量与状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurInstock extends BaseEntity {

    /** 状态：草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 状态：已审核 */
    public static final int STATUS_AUDITED = 10;

    /** 入库单号（RK+yyyyMMdd+4位流水，Redis 生成，唯一） */
    private String instockNo;

    /** 来源采购订单 ID（pur_order.id） */
    private Long orderId;

    /** 供应商 ID（base_supplier.id） */
    private Long supplierId;

    /** 入库仓库 ID（base_warehouse.id） */
    private Long warehouseId;

    /** 入库日期 */
    private LocalDate instockDate;

    /** 价税合计（明细含税金额之和） */
    private BigDecimal totalAmount;

    /** 状态：0草稿 10已审核 */
    private Integer status;

    /** 审核人（用户 ID，认证接入前为 0=系统） */
    private Long auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 备注 */
    private String remark;
}
