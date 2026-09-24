package com.erp.purchase.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 进项发票只读 Mapper——直查 inv_purchase_invoice 表（V941，归 invoice 模块所有）。
 * <p>
 * 跨模块约定：本模块【不 import invoice 模块代码】，只读 SELECT；
 * 暂估链路用途：①生成暂估前判断入库单是否已有生效蓝字发票；
 * ②冲回扫描器取已认证发票的票号与价税合计。
 */
public interface PurInvoiceRefMapper {

    /**
     * 入库单名下生效（待认证/已认证）蓝字发票数——大于 0 则无需暂估。
     */
    @Select("""
            SELECT COUNT(*) FROM inv_purchase_invoice
            WHERE biz_type = 'PUR_INSTOCK' AND biz_id = #{instockId}
              AND red_flag = 0 AND status IN (0, 10) AND deleted = 0
            """)
    long countActiveBlueInvoices(@Param("instockId") Long instockId);

    /**
     * 按发票 ID 取发票号（冲回扫描器幂等判断：该发票是否已冲回过暂估）。
     */
    @Select("SELECT invoice_no FROM inv_purchase_invoice WHERE id = #{id} AND deleted = 0")
    String selectInvoiceNoById(@Param("id") Long id);

    /**
     * 按发票 ID 取价税合计（冲回金额基数：发票实际金额）。
     */
    @Select("SELECT total_amount FROM inv_purchase_invoice WHERE id = #{id} AND deleted = 0")
    java.math.BigDecimal selectInvoiceTotalById(@Param("id") Long id);

    /**
     * 按发票 ID 取关联入库单 ID（biz_id）。
     */
    @Select("SELECT biz_id FROM inv_purchase_invoice WHERE id = #{id} AND deleted = 0")
    Long selectInstockIdByInvoice(@Param("id") Long id);
}
