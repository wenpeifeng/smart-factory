package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应付单实体（表 fin_payable）。
 * <p>
 * 来源：<b>采购模块入库后直接 INSERT</b>（并行协作约定，字段
 * supplier_id / biz_no / amount / settled_amount / status），
 * 本模块是表的 owner，负责付款核销回写与查询（与应收完全对称）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_payable")
public class FinPayable extends BaseEntity {

    /** 所属账套 ID（fin_book.id，多账套隔离键：Service 层统一注入查询条件与写入值）*/
    private Long bookId;

    /** 供应商 ID（base_supplier.id，名称查询时关联） */
    private Long supplierId;

    /** 来源业务单号（采购入库单号） */
    private String bizNo;

    /** 业务日期 */
    private LocalDate bizDate;

    /** 到期日 */
    private LocalDate dueDate;

    /** 应付金额 */
    private BigDecimal amount;

    /** 已核销金额（付款核销原子累加） */
    private BigDecimal settledAmount;

    /** 状态：UNSETTLED-未核销 PARTIAL-部分核销 SETTLED-已核销 */
    private String status;

    /** 备注 */
    private String remark;
}
