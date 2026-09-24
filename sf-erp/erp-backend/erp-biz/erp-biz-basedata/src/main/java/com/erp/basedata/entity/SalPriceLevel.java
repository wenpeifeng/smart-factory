package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售等级价实体（表 sal_price_level，V131）。
 * <p>
 * 物料 × 客户等级（A/B/C，对应 {@link BaseCustomer#getLevel()}）；
 * uk(material_id, level)；定价引擎优先级：特价 &gt; 等级价 &gt; 批量价 &gt; 参考成本加成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_price_level")
public class SalPriceLevel extends BaseEntity {

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 客户等级：A/B/C */
    private String level;

    /** 等级销售单价（不含税），DECIMAL(18,4) */
    private BigDecimal price;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
