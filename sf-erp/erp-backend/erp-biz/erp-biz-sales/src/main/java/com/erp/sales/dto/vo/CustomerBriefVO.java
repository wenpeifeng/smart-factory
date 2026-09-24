package com.erp.sales.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户基础信息简要 VO（内部使用）。
 * <p>
 * 销售订单新增/审核时用于校验客户存在性并读取信用额度，
 * 由自定义 SQL 只读查询 base_customer，不依赖 erp-basedata 代码。
 */
@Data
public class CustomerBriefVO {

    /** 客户 ID */
    private Long id;

    /** 客户编码 */
    private String code;

    /** 客户名称 */
    private String name;

    /** 信用额度（NULL 视为不控制） */
    private BigDecimal creditLimit;
}
