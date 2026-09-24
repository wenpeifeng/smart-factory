package com.erp.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售退货单明细行（表 sal_return_detail）。
 * <p>
 * 引用出库明细行（out_detail_id），退货数量不得超过出库行可退数量。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_return_detail")
public class SalReturnDetail extends BaseEntity {

    /** 退货单 ID（sal_return.id） */
    private Long returnId;

    /** 来源出库行 ID（sal_outstock_detail.id） */
    private Long outDetailId;

    /** 物料 ID */
    private Long materialId;

    /**
     * 退货入库批次号（V2101）：批次物料退货回补时透传给 StockService.stockIn
     * 落指定批次行；空串=非批次物料汇总行（批次物料缺批次号由 StockService 强校验拒绝）。
     */
    private String batchNo;

    /** 退货数量（正数） */
    private BigDecimal qty;

    /** 含税单价（取出库行单价） */
    private BigDecimal price;

    /** 金额 = qty * price（四舍五入 2 位，正数） */
    private BigDecimal amount;

    /** 行备注 */
    private String remark;
}
