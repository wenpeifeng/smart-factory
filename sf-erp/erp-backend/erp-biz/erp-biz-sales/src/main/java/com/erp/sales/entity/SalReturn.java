package com.erp.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售退货单（表 sal_return）。
 * <p>
 * 引用"已审核"销售出库单退货；审核成功在一个事务内完成：
 * 回补库存(inv_stock) → 写库存流水(inv_movement 正数) → 生成红字应收
 * (fin_receivable 负金额) → 回写出库行已退数量。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_return")
public class SalReturn extends BaseEntity {

    /** 退货单号（RT+yyyyMMdd+4位流水，唯一） */
    private String returnNo;

    /** 来源出库单 ID（sal_outstock.id） */
    private Long outId;

    /** 来源出库单号（冗余） */
    private String outNo;

    /** 客户 ID */
    private Long customerId;

    /** 退回仓库 ID */
    private Long warehouseId;

    /** 退货日期 */
    private LocalDate returnDate;

    /** 总数量（正数表述） */
    private BigDecimal totalQty;

    /** 总金额（正数表述；生成应收时取负值） */
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
