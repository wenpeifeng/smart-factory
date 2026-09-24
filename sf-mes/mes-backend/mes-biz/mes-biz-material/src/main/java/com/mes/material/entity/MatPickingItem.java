package com.mes.material.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 领料单行实体（表 mat_picking_item）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("mat_picking_item")
public class MatPickingItem extends BaseEntity {
    /** 领料单 ID */
    private Long pickingId;
    /** 物料 ID */
    private Long materialId;
    /** 批次号（空=批次不管理物料） */
    private String batchNo;
    /** 领料数量 */
    private BigDecimal qty;
    /** 备注 */
    private String remark;
}
