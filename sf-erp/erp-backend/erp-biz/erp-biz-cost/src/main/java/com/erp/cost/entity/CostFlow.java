package com.erp.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 存货计价流水 —— 移动加权平均法的运行台账，每一笔引起存货成本变动的业务落一条。
 * <p>
 * 关键约定：
 * <ul>
 *   <li>qty 正入负出，amount 与 qty 同号；unit_cost 恒为非负（移动加权成本）；</li>
 *   <li>balance_qty / balance_cost 是本行过账后该物料的运行余额（结存），
 *       同一物料的行按 id 升序构成一条链，最后一行即当前结存；</li>
 *   <li>计价维度是「物料 × 全仓」加权（与 inv_stock 的仓库维度互补），
 *       warehouse_id 仅作单据溯源；</li>
 *   <li>period 取业务日期（biz_date）所在月 yyyyMM，月末结账按期间汇总。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_flow")
public class CostFlow extends BaseEntity {

    /** 业务类型（取值见 {@link com.erp.cost.constant.CostBizType}） */
    private String bizType;

    /** 来源单据 ID（如 pur_instock.id / mrp_material_issue.id，手工补录可空） */
    private Long bizId;

    /** 来源单号（RK…/XS…/MI… 等） */
    private String bizNo;

    /** 关联库存流水 ID（inv_movement.id；cost/sync 重算时回填，手工补录为空） */
    private Long movementId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 仓库 ID（仅溯源，计价按物料全仓加权） */
    private Long warehouseId;

    /** 业务日期（period 的取值依据，空 = 入账当天） */
    private LocalDate bizDate;

    /** 数量：正数 = 入库，负数 = 出库 */
    private BigDecimal qty;

    /** 移动加权单价（非负，DECIMAL(18,6)） */
    private BigDecimal unitCost;

    /** 金额（与 qty 同号：入库为正，出库为负，DECIMAL(18,2)） */
    private BigDecimal amount;

    /** 运行余额：本行过账后结存数量 */
    private BigDecimal balanceQty;

    /** 运行余额：本行过账后结存金额 */
    private BigDecimal balanceCost;

    /** 期间 yyyyMM */
    private String period;
}
