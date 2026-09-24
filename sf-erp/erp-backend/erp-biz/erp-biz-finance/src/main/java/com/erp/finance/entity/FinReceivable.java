package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收单实体（表 fin_receivable）。
 * <p>
 * 来源：<b>销售模块出库后直接 INSERT</b>（并行协作约定，字段
 * customer_id / biz_no / amount / settled_amount / status），
 * 本模块（财务）是表的 owner，负责收款核销回写与查询。
 * 核销采用条件 UPDATE 原子累加 settled_amount，并联动状态：
 * 未核销 UNSETTLED → 部分核销 PARTIAL → 全额核销 SETTLED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_receivable")
public class FinReceivable extends BaseEntity {

    /** 所属账套 ID（fin_book.id，多账套隔离键：Service 层统一注入查询条件与写入值）*/
    private Long bookId;

    /** 客户 ID（base_customer.id，名称查询时关联） */
    private Long customerId;

    /** 来源业务单号（销售出库单号） */
    private String bizNo;

    /** 业务日期 */
    private LocalDate bizDate;

    /** 到期日 */
    private LocalDate dueDate;

    /** 应收金额 */
    private BigDecimal amount;

    /** 已核销金额（收款核销原子累加） */
    private BigDecimal settledAmount;

    /** 状态：UNSETTLED-未核销 PARTIAL-部分核销 SETTLED-已核销 */
    private String status;

    /** 备注 */
    private String remark;
}
