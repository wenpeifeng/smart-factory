package com.erp.sales.entity;

import com.erp.framework.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售订单（表 sal_order）。
 * <p>
 * 状态机：0草稿 → 10已审核 → 20部分出库 → 30已完成；10/20 → 90已关闭。
 * 审核时执行客户信用额度校验；金额/数量分别由明细行汇总而来。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_order")
public class SalOrder extends BaseEntity {

    /** 订单号（SO+yyyyMMdd+4位流水，唯一） */
    private String orderNo;

    /** 客户 ID（base_customer.id） */
    private Long customerId;

    /** 发货仓库 ID（base_warehouse.id） */
    private Long warehouseId;

    /** 订单日期 */
    private LocalDate orderDate;

    /** 总数量（明细 qty 合计） */
    private BigDecimal totalQty;

    /** 总金额（明细 amount 合计） */
    private BigDecimal totalAmount;

    /** 状态：0草稿 10已审核 20部分出库 30已完成 90已关闭 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 审核人 */
    private Long auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 关闭人 */
    private Long closeBy;

    /** 关闭时间 */
    private LocalDateTime closeTime;

    /**
     * 审批流发起标记（三段式审核依据，V2101）：
     * 0=从未发起过流程；1=已发起过（再次点审核按 WorkflowService.isApproved 判定，
     * 审批通过即执行原审核；驳回后重新点审核=重新发起新实例）。
     */
    private Integer wfStarted;
}
