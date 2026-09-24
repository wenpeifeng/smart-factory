package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 付款单主实体（表 fin_payment）。
 * <p>
 * 与收款单对称：一张付款单选择同一供应商的多笔应付分配核销，
 * 保存后原子回写应付状态、扣减资金账户余额（余额不足则整体回滚）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_payment")
public class FinPayment extends BaseEntity {

    /** 所属账套 ID（fin_book.id，多账套隔离键：Service 层统一注入查询条件与写入值）*/
    private Long bookId;

    /** 付款单号（FK + yyyyMMdd + 4 位流水，Redis 自增生成） */
    private String paymentNo;

    /** 供应商 ID */
    private Long supplierId;

    /** 付款资金账户 ID */
    private Long accountId;

    /** 付款日期 */
    private LocalDate bizDate;

    /** 付款金额（必须等于明细核销额之和） */
    private BigDecimal amount;

    /** 已核销金额（当前模型下恒等于付款金额） */
    private BigDecimal verifiedAmount;

    /** 备注 */
    private String remark;

    /** 费用归属部门（sys_dept.id，预算控制用；V504 加列，空=跳过预算校验，兼容老数据） */
    private Long deptId;

    /** 预算科目（fin_subject.id，可空；V504 加列，空=跳过预算校验） */
    private Long subjectId;
}
