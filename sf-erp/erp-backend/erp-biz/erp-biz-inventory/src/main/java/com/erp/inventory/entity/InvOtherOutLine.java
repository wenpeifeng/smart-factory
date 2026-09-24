package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 其他出库单明细行 —— 一行一个物料。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_other_out_line")
public class InvOtherOutLine extends BaseEntity {

    /** 头表 ID（inv_other_out.id） */
    private Long otherOutId;

    /** 物料 ID */
    private Long materialId;

    /** 批次号（指定批次出库时必填；空串 = 批次物料按 FIFO 自动分配，V411 起） */
    private String batchNo;

    /** 出库数量（正数） */
    private BigDecimal qty;

    /** 行备注 */
    private String remark;
}
