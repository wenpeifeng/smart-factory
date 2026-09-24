package com.erp.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 存货核算期间 —— 月末结账状态机。
 * <p>
 * status 单向流转 OPEN → CLOSED：结账（POST /cost/settle/{period}）时
 * 快照本期出库成本并自动生成转账凭证；CLOSED 期间禁止再过账计价流水
 * （{@code CostService#onStockIn/onStockOut} 校验拦截），保证结账数据不被追溯污染。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_period")
public class CostPeriod extends BaseEntity {

    /** 期间 yyyyMM（唯一） */
    private String period;

    /** 状态：OPEN / CLOSED（见 {@link com.erp.cost.constant.CostConstants}） */
    private String status;

    /** 本期出库成本合计（结账时快照，正数列示） */
    private BigDecimal outAmount;

    /** 结账生成的转账凭证 ID（无凭证为空） */
    private Long voucherId;

    /** 结账生成的转账凭证号 */
    private String voucherNo;

    /** 结账人（用户 ID，0 = 系统） */
    private Long settleBy;

    /** 结账时间 */
    private LocalDateTime settleTime;

    /** 备注 */
    private String remark;
}
