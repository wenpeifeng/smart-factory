package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售批量价实体（表 sal_price_qty，V131）。
 * <p>
 * 物料 × 起订量阶梯：uk(material_id, min_qty)；
 * 定价引擎取「满足 qty 的最高 min_qty 阶梯」（即满足数量的最低价格档）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_price_qty")
public class SalPriceQty extends BaseEntity {

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 起订量（含；qty ≥ min_qty 即命中该档） */
    private BigDecimal minQty;

    /** 批量销售单价（不含税），DECIMAL(18,4) */
    private BigDecimal price;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
