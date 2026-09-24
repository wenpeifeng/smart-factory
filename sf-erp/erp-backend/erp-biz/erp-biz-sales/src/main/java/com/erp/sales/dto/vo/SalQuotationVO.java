package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售报价单表头 VO（分页/详情通用）。
 * <p>
 * status 为"有效状态"：已报价但过有效期时返回 30已过期（SQL 动态判定），
 * 前端据此渲染；rawStatus 为落库原始状态。
 */
@Data
public class SalQuotationVO {

    /** 报价单 ID */
    private Long id;

    /** 报价单号（BJ+yyyyMMdd+流水） */
    private String quotationNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码（JOIN base_customer） */
    private String customerCode;

    /** 客户名称（JOIN base_customer） */
    private String customerName;

    /** 报价日期 */
    private LocalDate quoteDate;

    /** 有效期至 */
    private LocalDate validUntil;

    /** 整单折扣率%（空 = 不打折） */
    private BigDecimal discountRate;

    /** 有效状态：0草稿 10已报价 20已转订单 30已过期(动态) 40已作废 */
    private Integer status;

    /** 落库原始状态（30已过期不会出现在该字段） */
    private Integer rawStatus;

    /** 下推生成的销售订单 ID */
    private Long orderId;

    /** 下推生成的销售订单号 */
    private String orderNo;

    /** 提交报价时间 */
    private LocalDateTime submitTime;

    /** 作废时间 */
    private LocalDateTime voidTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
