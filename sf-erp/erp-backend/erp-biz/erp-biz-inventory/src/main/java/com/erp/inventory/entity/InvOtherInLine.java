package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 其他入库单明细行 —— 一行一个物料。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_other_in_line")
public class InvOtherInLine extends BaseEntity {

    /** 头表 ID（inv_other_in.id） */
    private Long otherInId;

    /** 物料 ID */
    private Long materialId;

    /** 批次号（批次物料必填；非批次物料空串，V411 起） */
    private String batchNo;

    /** 效期（批次行 FEFO 出库依据；可空，V422 起） */
    private java.time.LocalDate expiryDate;

    /** 入库数量（正数） */
    private BigDecimal qty;

    /** 行备注 */
    private String remark;
}
