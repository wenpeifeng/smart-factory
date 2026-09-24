package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 委外加工单（简化模型）—— 状态机：DRAFT草稿 → ISSUED已发料 → RECEIVED已完工入库。
 * <p>
 * 发料：按 BOM 子件扣减发料仓库库存（inv_movement biz_type=OUT_USE）；
 * 完工入库：成品入库（OUT_IN）并按 数量×加工费单价 生成应付（fin_payable，财务模块集成点）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_outsource")
public class MrpOutsource extends BaseEntity implements Serializable {

    /** 单号（OS+yyyyMMdd+4位流水） */
    private String orderNo;

    /** 供应商ID(base_supplier.id) */
    private Long supplierId;

    /** 加工后成品物料ID */
    private Long materialId;

    /** 委外加工数量 */
    private BigDecimal qty;

    /** BOMID（发料子件依据） */
    private Long bomId;

    /** BOM版本号快照 */
    private String bomVersion;

    /** 发料仓库 */
    private Long issueWarehouseId;

    /** 完工入库仓库 */
    private Long inWarehouseId;

    /** 加工费单价（元/件） */
    private BigDecimal feeUnitPrice;

    /** 状态：DRAFT ISSUED RECEIVED */
    private String status;

    /** 备注 */
    private String remark;
}
