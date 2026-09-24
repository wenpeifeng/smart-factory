package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MRP 运算展开明细 —— 记录 BOM 逐级展开后的全树过程数据
 * （毛需求/库存快照/在途快照/净需求/建议），供按批次查看层级明细。
 * <p>
 * node_path / parent_path 记录“根物料ID/…/当前物料ID”的路径串，
 * 既用于 BOM 防环检测，也用于前端按批次组装树。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_calc_detail")
public class MrpCalcDetail extends BaseEntity implements Serializable {

    /** 运算批次号(mrp_calc.batch_no) */
    private String batchNo;

    /** 来源 MPS 行ID（一次运算多成品时各自成树） */
    private Long mpsId;

    /** BOM层级：0=成品 1=一级子件… */
    private Integer level;

    /** 物料ID */
    private Long materialId;

    /** 毛需求 */
    private BigDecimal grossQty;

    /** 运算时现有库存快照（inv_stock 全仓汇总） */
    private BigDecimal stockQty;

    /** 运算时在途数量快照（采购在途） */
    private BigDecimal onwayQty;

    /** 净需求 = 毛需求 − 可用(库存+在途)扣抵，负数归零 */
    private BigDecimal netQty;

    /** 建议数量（无建议为 NULL） */
    private BigDecimal suggestQty;

    /** 建议日期 = 需求日期 − 提前期(7天) */
    private LocalDate suggestDate;

    /** 建议类型：PURCHASE / PRODUCE（无建议为 NULL） */
    private String planType;

    /** 树节点路径（根到当前物料ID串） */
    private String nodePath;

    /** 父节点路径（根节点为 NULL） */
    private String parentPath;
}
