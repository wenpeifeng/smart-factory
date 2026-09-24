package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 生产领料单头 —— 草稿创建（按 BOM 自动带出应领数量）→ 审核扣库存。
 * <p>
 * 审核成功即写 inv_movement(biz_type=PROD_USE)，同时生产订单进入 PRODUCING。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_material_issue")
public class MrpMaterialIssue extends BaseEntity implements Serializable {

    /** 单号（MI+yyyyMMdd+4位流水） */
    private String issueNo;

    /** 生产订单ID(mrp_work_order.id) */
    private Long workOrderId;

    /** 发料仓库(base_warehouse.id) */
    private Long warehouseId;

    /** 单据类型：NORMAL手工领料 / BACKFLUSH完工倒冲自动生成（V621） */
    private String issueType;

    /** 状态：DRAFT草稿 AUDITED已审核(已扣库存) */
    private String status;

    /** 备注 */
    private String remark;
}
