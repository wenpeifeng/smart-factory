package com.erp.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售出库单（表 sal_outstock）。
 * <p>
 * 从"已审核/部分出库"销售订单选单生成；审核成功在一个事务内完成：
 * 扣减库存(inv_stock) → 写库存流水(inv_movement 负数) → 生成应收(fin_receivable)
 * → 回写订单行出库数量并刷新订单状态。库存不足抛 BusinessException 整体回滚。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_outstock")
public class SalOutstock extends BaseEntity {

    /** 出库单号（XS+yyyyMMdd+4位流水，唯一） */
    private String outNo;

    /** 来源销售订单 ID（sal_order.id） */
    private Long orderId;

    /** 来源销售订单号（冗余） */
    private String orderNo;

    /** 客户 ID */
    private Long customerId;

    /** 出库仓库 ID */
    private Long warehouseId;

    /** 出库日期 */
    private LocalDate outDate;

    /** 总数量 */
    private BigDecimal totalQty;

    /** 总金额 */
    private BigDecimal totalAmount;

    /** 状态：0草稿 10已审核 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 审核人 */
    private Long auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;
}
