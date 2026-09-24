package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 应付单实体（fin_payable）——【跨模块约定表，归 finance 模块所有，结构见 V501】。
 * <p>
 * 采购模块写入时机：采购入库单审核通过时 INSERT 一条未核销应付
 * （supplier_id=入库供应商，biz_no=入库单号，amount=入库单价税合计）。
 * status 为 finance 模块定义的字符串枚举：UNSETTLED / PARTIAL / SETTLED，
 * 结算与核销归 finance 模块，本模块不做任何 UPDATE。
 */
@Data
@TableName("fin_payable")
public class FinPayable {

    /** 状态：未核销（finance V501 约定值） */
    public static final String STATUS_UNSETTLED = "UNSETTLED";

    /** 供应商 ID（base_supplier.id） */
    private Long supplierId;

    /** 来源业务单据号（采购入库单号 RK...，或暂估链路 ZG 单号，V231） */
    private String bizNo;

    /** 业务日期（V231 暂估链路写入：暂估=入库日期，冲回三行=冲回当日；入库审核链路仍留空） */
    private java.time.LocalDate bizDate;

    /** 应付金额（价税合计；暂估红字行为负数，V231） */
    private BigDecimal amount;

    /** 已核销金额 */
    private BigDecimal settledAmount;

    /** 状态：UNSETTLED未核销 PARTIAL部分核销 SETTLED已核销 */
    private String status;

    /** 备注（V231 暂估链路写入来源标记：暂估应付/暂估红冲/票到入账/暂估差额调整） */
    private String remark;
}
