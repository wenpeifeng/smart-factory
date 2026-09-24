package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 客户实体（表 base_customer）。
 * <p>
 * 信用额度供销售模块下单时校验（应收余额 + 本次金额 不超 creditLimit）；
 * 期初应收供财务应收模块初始化余额。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_customer")
public class BaseCustomer extends BaseEntity {

    /** 客户编码，全局唯一（自动生成规则：KH+日期+3位流水） */
    private String code;

    /** 客户名称 */
    private String name;

    /** 联系人 */
    private String contact;

    /** 手机号 */
    private String phone;

    /** 地址 */
    private String address;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 结算方式 ID（base_settle_type.id） */
    private Long settleTypeId;

    /** 信用额度，DECIMAL(18,2) */
    private BigDecimal creditLimit;

    /** 期初应收，DECIMAL(18,2) */
    private BigDecimal initReceivable;

    /** 客户等级：A/B/C（销售等级价策略匹配，V131 加列，存量默认 B） */
    private String level;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
