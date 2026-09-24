package com.mes.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 供应商实体（表 base_supplier）。
 * <p>
 * 期初应付供财务应付模块初始化余额；字段结构与客户基本对称（编码前缀 GYS）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_supplier")
public class BaseSupplier extends BaseEntity {

    /** 供应商编码，全局唯一（自动生成规则：GYS+日期+3位流水） */
    private String code;

    /** 供应商名称 */
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

    /** 期初应付，DECIMAL(18,2) */
    private BigDecimal initPayable;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
