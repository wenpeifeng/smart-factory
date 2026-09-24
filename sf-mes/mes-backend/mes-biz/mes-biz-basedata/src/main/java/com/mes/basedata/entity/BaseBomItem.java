package com.mes.basedata.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * BOM 子件明细（表 base_bom_item）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("base_bom_item")
public class BaseBomItem extends BaseEntity {
    /** BOM 头 ID（base_bom.id） */
    private Long bomId;
    /** 子件物料 ID（base_material.id） */
    private Long materialId;
    /** 单位用量（单件成品耗用子件数量） */
    private BigDecimal qty;
    /** 损耗率 %（0-100） */
    private BigDecimal lossRate;
    /** 备注 */
    private String remark;
}
