package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购合同价实体（表 base_price_contract，V131）。
 * <p>
 * 录单参照时合同价优先展示（前端标注"合同"）；
 * 生效窗口 [effectiveFrom, effectiveTo] 含两端，仅 status=1 的记录参与参照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_price_contract")
public class BasePriceContract extends BaseEntity {

    /** 供应商 ID（base_supplier.id） */
    private Long supplierId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 合同单价（不含税），DECIMAL(18,4) */
    private BigDecimal contractPrice;

    /** 生效日期起（含） */
    private LocalDate effectiveFrom;

    /** 生效日期止（含） */
    private LocalDate effectiveTo;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注（合同号等） */
    private String remark;
}
