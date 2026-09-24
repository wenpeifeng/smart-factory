package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购暂估应付单实体（表 pur_estimation，V231）——货到票未到场景。
 * <p>
 * 一个已审核采购入库单至多一条暂估（uk: instock_id+deleted）：
 * 暂估金额=Σ 明细数量×含税单价÷(1+税率%)（不含税净额，HALF_UP 2 位）；
 * 票到后按发票金额冲回（红字 fin_payable + 正式应付 + 差额调整行），
 * 状态 0暂估中 → 10已冲回(全额)/20部分冲回(可继续)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pur_estimation")
public class PurEstimation extends BaseEntity {

    /** 状态：暂估中（尚未冲回） */
    public static final int STATUS_OPEN = 0;
    /** 状态：已冲回（全额，终态） */
    public static final int STATUS_REVERSED = 10;
    /** 状态：部分冲回（发票未全额覆盖，可继续冲回） */
    public static final int STATUS_PARTIAL = 20;

    /** 暂估单号（ZG+yyyyMMdd+3位流水） */
    private String estimationNo;

    /** 采购入库单 ID（pur_instock.id） */
    private Long instockId;

    /** 采购入库单号（RK...，冗余便于报表检索） */
    private String instockNo;

    /** 供应商 ID（base_supplier.id） */
    private Long supplierId;

    /** 暂估金额（不含税） */
    private BigDecimal amount;

    /** 累计已冲回金额（<= amount） */
    private BigDecimal reversedAmount;

    /** 状态：0暂估中 10已冲回 20部分冲回 */
    private Integer status;

    /** 暂估期间 yyyyMM */
    private String period;

    /** 最近一次冲回所用发票号（未冲回为 null） */
    private String invoiceNo;

    /** 已冲回所用发票号清单（逗号分隔，防同一发票重复冲回） */
    private String reversedInvoiceNos;

    /** 备注 */
    private String remark;
}
