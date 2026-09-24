package com.erp.purchase.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购退货单明细实体（pur_return_detail）。
 * <p>
 * 退货数量必须 <= 来源入库明细数量 - 该明细累计已退数量（服务层校验），
 * 保证库存回退与应付冲减口径不出现负数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurReturnDetail extends BaseEntity {

    /** 退货单 ID（pur_return.id） */
    private Long returnId;

    /** 来源入库明细 ID（pur_instock_detail.id） */
    private Long instockDetailId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 退货数量（>0） */
    private BigDecimal qty;

    /** 含税单价（默认取入库单价） */
    private BigDecimal price;

    /** 税率（百分数） */
    private BigDecimal taxRate;

    /** 含税金额 = qty × price，HALF_UP 2 位 */
    private BigDecimal amount;

    /** 备注 */
    private String remark;
}
