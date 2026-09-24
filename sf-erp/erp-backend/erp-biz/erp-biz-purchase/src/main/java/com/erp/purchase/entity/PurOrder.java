package com.erp.purchase.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购订单主表实体（pur_order）。
 * <p>
 * 状态机（只允许正向流转，见 PurOrderServiceImpl）：
 * 0草稿 -> 10已审核 -> 20部分入库 -> 30已完成；10/20 可人工关闭为 90已关闭。
 * 价税合计 = 明细含税金额之和，由服务端计算，禁止前端传入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurOrder extends BaseEntity {

    /** 状态：草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 状态：已审核 */
    public static final int STATUS_AUDITED = 10;
    /** 状态：部分入库 */
    public static final int STATUS_PART_INSTOCK = 20;
    /** 状态：已完成（全部入库） */
    public static final int STATUS_FINISHED = 30;
    /** 状态：已关闭 */
    public static final int STATUS_CLOSED = 90;

    /** 订单号（PO+yyyyMMdd+4位流水，Redis 生成，唯一） */
    private String orderNo;

    /** 供应商 ID（base_supplier.id） */
    private Long supplierId;

    /** 默认入库仓库 ID（base_warehouse.id） */
    private Long warehouseId;

    /** 订单日期 */
    private LocalDate orderDate;

    /** 价税合计（明细含税金额之和） */
    private BigDecimal totalAmount;

    /** 状态：0草稿 10已审核 20部分入库 30已完成 90已关闭 */
    private Integer status;

    /** 审核人（用户 ID，SecurityUtils.getCurrentUserId()，未登录上下文为 0=系统） */
    private Long auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /**
     * 审批流发起标记（三段式审核依据，V2101）：
     * 0=从未发起过流程（首次点审核先 startFlow）；
     * 1=已发起过（再次点审核按 WorkflowService.isApproved 判定，
     * 审批通过即执行原审核；驳回后重新点审核=重新发起新实例）。
     * 单据在流程走完前保持草稿态，不引入"审批中"单据状态。
     */
    private Integer wfStarted;

    /** 备注 */
    private String remark;
}
