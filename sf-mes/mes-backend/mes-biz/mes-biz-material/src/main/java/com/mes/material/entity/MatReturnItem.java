package com.mes.material.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 退料单行实体（表 mat_return_item）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("mat_return_item")
public class MatReturnItem extends BaseEntity {
    /** 退料单 ID */
    private Long returnId;
    /** 物料 ID */
    private Long materialId;
    /** 批次号 */
    private String batchNo;
    /** 退料数量 */
    private BigDecimal qty;
    /** 备注 */
    private String remark;
}
