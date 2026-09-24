package com.erp.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售报价单头（表 sal_quotation，V3111）。
 * <p>
 * 状态机：0草稿 →(提交报价)→ 10已报价 →(下推销售订单)→ 20已转订单（终态）；
 * 10 且 valid_until 早于当天 = 30已过期（查询时动态判定，不落库）；
 * 0/10 →(作废)→ 40已作废（终态）。
 * 行金额 = 数量 × 报价单价 × (1 − 整单折扣率/100)，四舍五入 2 位。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_quotation")
public class SalQuotation extends BaseEntity {

    /** 报价单号（BJ+yyyyMMdd+流水，唯一） */
    private String quotationNo;

    /** 客户 ID（base_customer.id） */
    private Long customerId;

    /** 报价日期 */
    private LocalDate quoteDate;

    /** 有效期至（当天含在内；过期 = valid_until 早于当天） */
    private LocalDate validUntil;

    /** 整单折扣率%（0-100，如 10 表示九折；空 = 不打折） */
    private BigDecimal discountRate;

    /** 状态：0草稿 10已报价 20已转订单 40已作废（30已过期为查询时动态判定） */
    private Integer status;

    /** 下推生成的销售订单 ID（sal_order.id） */
    private Long orderId;

    /** 下推生成的销售订单号 */
    private String orderNo;

    /** 提交报价人 */
    private Long submitBy;

    /** 提交报价时间 */
    private LocalDateTime submitTime;

    /** 作废人 */
    private Long voidBy;

    /** 作废时间 */
    private LocalDateTime voidTime;

    /** 备注 */
    private String remark;
}
