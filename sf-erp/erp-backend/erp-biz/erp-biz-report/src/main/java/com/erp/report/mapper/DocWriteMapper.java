package com.erp.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 通用红冲回写 Mapper（XML：resources/mapper/report/DocWriteMapper.xml）。
 * <p>
 * 只做红冲专用的<b>带守卫条件 UPDATE</b>（0 行=业务冲突，调用方抛异常回滚）：
 * 单据状态标记红冲、订单/明细数量回退、应收应付核销回冲。
 * 表名白名单校验在 Service 层完成（{@code ${table}} 仅接受 DocTypes.STATUS_TABLES 的值）。
 */
@Mapper
public interface DocWriteMapper {

    /**
     * 原单标记已红冲：status 10 → 20（条件更新防并发重复红冲）。
     *
     * @param table 表名（白名单：pur_instock/sal_outstock/pur_return/sal_return）
     */
    int markReversed(@Param("table") String table,
                     @Param("id") Long id,
                     @Param("operator") Long operator);

    /** 采购订单累计入库数回退：received_qty + delta >= 0 守卫（红冲入库 delta 为负 / 红冲退货为正） */
    int adjustReceivedQty(@Param("orderDetailId") Long orderDetailId,
                          @Param("delta") BigDecimal delta);

    /** 销售订单行累计出库数回退：out_qty + delta >= 0 守卫 */
    int adjustOutQty(@Param("orderDetailId") Long orderDetailId,
                     @Param("delta") BigDecimal delta);

    /** 入库/出库明细已退数量回退：returned_qty + delta >= 0 守卫 */
    int adjustReturnedQty(@Param("table") String table,
                          @Param("detailId") Long detailId,
                          @Param("delta") BigDecimal delta);

    /** 应收核销回冲：settled_amount - amt >= 0 守卫，状态联动 UNSETTLED/PARTIAL */
    int unverifyReceivable(@Param("receivableId") Long receivableId,
                           @Param("amount") BigDecimal amount);

    /** 应付核销回冲（与应收对称） */
    int unverifyPayable(@Param("payableId") Long payableId,
                        @Param("amount") BigDecimal amount);
}
