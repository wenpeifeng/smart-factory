package com.mes.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 结算方式实体（表 base_settle_type）。
 * <p>
 * 内置：现金/转账/承兑/月结；账期天数供应收应付模块计算到期日。
 * 被客户/供应商引用后禁止删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_settle_type")
public class BaseSettleType extends BaseEntity {

    /** 方式编码，全局唯一（如 XJ/ZZ/CD/YJ） */
    private String code;

    /** 方式名称（现金/转账/承兑/月结等） */
    private String name;

    /** 账期天数（月结30 = 30 天） */
    private Integer days;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
