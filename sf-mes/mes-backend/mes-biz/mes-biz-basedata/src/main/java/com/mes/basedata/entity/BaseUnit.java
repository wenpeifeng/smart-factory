package com.mes.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 计量单位实体（表 base_unit）。
 * <p>
 * 换算规则：同 {@code groupCode} 的单位为一个换算组，组内换算公式为
 * {@code 目标数量 = 数量 * 源单位.rate / 目标单位.rate}；
 * 每组有且仅有一个基准单位（isBase=1 且 rate=1）。
 * 例：数量组「个(1)/打(12)/箱(10)」中 2打 = 2*12/1 = 24个。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_unit")
public class BaseUnit extends BaseEntity {

    /** 换算组编码，同组单位可互相换算（如 NUM=数量组/WT=重量组） */
    private String groupCode;

    /** 单位名称（如：个/箱/千克） */
    private String name;

    /** 换算比率（相对组内基准单位，基准单位=1） */
    private BigDecimal rate;

    /** 是否基准单位：1-是 0-否 */
    private Integer isBase;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
