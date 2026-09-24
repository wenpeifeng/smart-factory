package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 总账会计期间实体（表 fin_period，V521）。
 * <p>
 * 期间 = 凭证日期所在自然月（yyyyMM），按账套隔离（uk(book_id, period)）。
 * CLOSED 期间禁止新增/修改/过账/删除该期凭证（FinVoucherServiceImpl 各入口
 * 调用 FinPeriodService 校验）；结账要求该期凭证全部 POSTED。
 * <p>
 * 注意：与 erp-cost 的 cost_period（成本计算期间）是两个独立概念，互不影响。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_period")
public class FinPeriod extends BaseEntity {

    /** 期间状态：开启 */
    public static final String STATUS_OPEN = "OPEN";
    /** 期间状态：已结账 */
    public static final String STATUS_CLOSED = "CLOSED";

    /** 所属账套 ID（fin_book.id，期间按账套隔离） */
    private Long bookId;

    /** 会计期间（yyyyMM，凭证日期所在自然月） */
    private String period;

    /** 状态：OPEN 开启 / CLOSED 已结账 */
    private String status;

    /** 结账人 */
    private Long closeBy;

    /** 结账时间 */
    private LocalDateTime closeTime;

    /** 最近反结账人 */
    private Long reopenBy;

    /** 最近反结账时间 */
    private LocalDateTime reopenTime;

    /** 备注 */
    private String remark;
}
