package com.erp.purchase.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 请购单明细实体（pur_requisition_item）。
 * <p>
 * 「已转采购数量」守卫：ordered_qty 只在询比价定标事务内通过条件 UPDATE 回写
 * （ordered_qty + delta &lt;= qty，影响 0 行即回滚），从数据库层杜绝累计超转；
 * 应用层在 RFQ 创建时同样校验 qty &lt;= 请购数量 - 已转数量。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurRequisitionItem extends BaseEntity {

    /** 请购单 ID（pur_requisition.id） */
    private Long requisitionId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 请购数量（>0） */
    private BigDecimal qty;

    /** 已转采购数量（询比价定标回写，累计 <= qty） */
    private BigDecimal orderedQty;

    /** 需求日期 */
    private LocalDate requireDate;

    /** 备注 */
    private String remark;
}
