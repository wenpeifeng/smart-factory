package com.erp.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 单据打印中心聚合 Mapper（XML：resources/mapper/report/PrintMapper.xml）。
 * <p>
 * 职责：为 5 类打印模板（销售订单/送货单/采购订单/月度对账单/记账凭证）
 * 做<b>只读</b>主子表聚合。与 {@link ReportMapper} 同样的跨模块约定：
 * 不 import 业务模块代码，直接按各模块建表脚本约定列 JOIN，
 * 全部过滤 deleted = 0，金额 COALESCE 兜底。
 * <p>
 * 打印口径与报表统计不同：打印是"单据自身"的呈现，草稿单也可打印
 * （纸质流转场景），因此主子表查询不做状态过滤；仅对账单按已审核出库
 * 汇总（对账口径与 /report/sales 明细一致）。
 */
@Mapper
public interface PrintMapper {

    // ==================== 打印单据选择列表（分页 count + rows） ====================

    /** 销售订单可选数（keyword 匹配订单号/客户名） */
    long countSalOrders(@Param("keyword") String keyword);

    /** 销售订单列表行：id/orderNo/customerName/orderDate/totalAmount/status/makerName */
    List<Map<String, Object>> listSalOrders(@Param("keyword") String keyword,
                                            @Param("offset") long offset,
                                            @Param("limit") int limit);

    /** 销售出库单可选数 */
    long countOutstocks(@Param("keyword") String keyword);

    /** 销售出库单列表行：id/outNo/customerName/outDate/totalAmount/status/makerName */
    List<Map<String, Object>> listOutstocks(@Param("keyword") String keyword,
                                            @Param("offset") long offset,
                                            @Param("limit") int limit);

    /** 采购订单可选数 */
    long countPurOrders(@Param("keyword") String keyword);

    /** 采购订单列表行：id/orderNo/supplierName/orderDate/totalAmount/status/makerName */
    List<Map<String, Object>> listPurOrders(@Param("keyword") String keyword,
                                            @Param("offset") long offset,
                                            @Param("limit") int limit);

    /** 记账凭证可选数 */
    long countVouchers(@Param("keyword") String keyword);

    /** 记账凭证列表行：id/voucherNo/voucherDate/totalDebit/status/word/makerName */
    List<Map<String, Object>> listVouchers(@Param("keyword") String keyword,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);

    /** 对账单可选客户数（当月有已审核出库的客户） */
    long countStatementCustomers(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 对账单列表行（当月有已审核出库的客户）：id=customerId, billNo=客户编码,
     * partyName=客户名, amount=当月应收发生额, billCount=当月出库单数
     */
    List<Map<String, Object>> listStatementCustomers(@Param("from") LocalDate from,
                                                     @Param("to") LocalDate to,
                                                     @Param("offset") long offset,
                                                     @Param("limit") int limit);

    // ==================== 销售订单（sal_order）主子表 ====================

    /**
     * 销售订单打印头：单据主表 + 客户(code/name/contact/phone/address) + 仓库 + 制单/审核人。
     */
    Map<String, Object> selectSalOrder(@Param("id") Long id);

    /** 销售订单明细行：matCode/matName/matSpec/unitName/qty/price/amount/remark */
    List<Map<String, Object>> selectSalOrderDetails(@Param("orderId") Long orderId);

    // ==================== 销售出库单（sal_outstock，送货单样式） ====================

    /** 出库单打印头：主表 + 客户 + 仓库 + 关联订单号 + 制单/审核人 */
    Map<String, Object> selectOutstock(@Param("id") Long id);

    /** 出库单明细行：matCode/matName/matSpec/unitName/qty/price/amount/remark */
    List<Map<String, Object>> selectOutstockDetails(@Param("outId") Long outId);

    // ==================== 采购订单（pur_order）主子表 ====================

    /** 采购订单打印头：主表 + 供应商(code/name/contact/phone/address) + 仓库 + 制单/审核人 */
    Map<String, Object> selectPurOrder(@Param("id") Long id);

    /** 采购订单明细行：matCode/matName/matSpec/unitName/qty/price/taxRate/amount/deliveryDate/remark */
    List<Map<String, Object>> selectPurOrderDetails(@Param("orderId") Long orderId);

    // ==================== 记账凭证（fin_voucher）主子表 ====================

    /** 凭证打印头：主表( voucherNo/word/attachmentCount/totalDebit/totalCredit/status/voucherDate) + 制单/审核人 */
    Map<String, Object> selectVoucher(@Param("id") Long id);

    /** 凭证分录行：lineNo/summary/subjectCode/subjectName/debitAmount/creditAmount */
    List<Map<String, Object>> selectVoucherEntries(@Param("voucherId") Long voucherId);

    // ==================== 客户月度对账单（statement） ====================

    /** 客户信息头：code/name/contact/phone/address */
    Map<String, Object> selectCustomer(@Param("id") Long id);

    /**
     * 对账单明细行：该客户当月已审核出库的明细
     * outNo/orderNo/outDate/matCode/matName/qty/amount
     */
    List<Map<String, Object>> selectStatementRows(@Param("customerId") Long customerId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    /**
     * 对账单期初应收 = base_customer.init_receivable + 月初之前全部已审核出库合计。
     * （收款核销不回溯历史期间，与报表中心应收口径一致，见 ReportMapper 注释。）
     */
    BigDecimal selectCustomerOpening(@Param("customerId") Long customerId, @Param("from") LocalDate from);
}
