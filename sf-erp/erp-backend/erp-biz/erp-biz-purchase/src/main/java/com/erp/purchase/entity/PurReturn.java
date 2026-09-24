package com.erp.purchase.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购退货单主表实体（pur_return）。
 * <p>
 * 引用已审核的采购入库单退货，状态机：0草稿 -> 10已审核。
 * 审核时在一个事务内：写反向库存流水（qty 为负）、条件 UPDATE 扣减 inv_stock
 * （qty+delta>=0 防负库存，不足则整体回滚）、回写入库明细已退数量与订单累计入库数量。
 * 退货不生成负应付（红字方案联调时与 finance 模块确认，见 docs/modules/purchase.md）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurReturn extends BaseEntity {

    /** 状态：草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 状态：已审核 */
    public static final int STATUS_AUDITED = 10;

    /** 退货单号（RT+yyyyMMdd+4位流水，Redis 生成，唯一） */
    private String returnNo;

    /** 来源入库单 ID（pur_instock.id） */
    private Long instockId;

    /** 供应商 ID（base_supplier.id） */
    private Long supplierId;

    /** 退货出库仓库 ID（base_warehouse.id，取自入库单） */
    private Long warehouseId;

    /** 退货日期 */
    private LocalDate returnDate;

    /** 价税合计（明细含税金额之和，退货冲减口径） */
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
