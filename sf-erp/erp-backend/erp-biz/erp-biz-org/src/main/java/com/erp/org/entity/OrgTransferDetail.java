package com.erp.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 组织间调拨单明细（org_transfer_detail）。
 * <p>
 * 保存时由服务端按「cost_flow 最近加权成本 + 价目规则」计算并快照三个价格列，
 * 审核过账与统计均以快照为准（草稿期成本变动不回溯已保存单据，重存即重算）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("org_transfer_detail")
public class OrgTransferDetail extends BaseEntity {

    /** 调拨单 ID（org_transfer.id） */
    private Long transferId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 调拨数量（>0） */
    private BigDecimal qty;

    /** 成本单价：调出方最近加权价 = cost_flow 最新 balance_cost / balance_qty（无数据为 0） */
    private BigDecimal costPrice;

    /** 内部结算单价：RATE=成本×(1+加价率%) FIXED=成本+固定额（无价目=成本） */
    private BigDecimal settlePrice;

    /** 内部结算金额 = 结算单价 × 数量（HALF_UP 2 位） */
    private BigDecimal settleAmount;
}
