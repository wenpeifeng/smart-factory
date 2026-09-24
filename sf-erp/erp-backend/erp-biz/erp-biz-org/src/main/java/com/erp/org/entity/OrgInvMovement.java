package com.erp.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库存流水写入实体（inv_movement，inventory 模块约定表）。
 * <p>
 * 跨模块约定（docs/modules/inventory.md「过渡期兼容」）：
 * 组织间调拨不 import inventory 代码，按约定列直接 INSERT——
 * qty 正入负出（ORG_OUT 为负、ORG_IN 为正）、biz_id=调拨单 id、
 * biz_no=IT 单号、remark 记 IT 单号；batch_no 列依赖库默认值 ''（V411 起 NOT NULL DEFAULT ''）。
 * create_by/create_time 等审计列由 MP 审计填充器自动写入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_movement")
public class OrgInvMovement extends BaseEntity {

    /** 业务类型：ORG_OUT 组织间调拨出库 / ORG_IN 组织间调拨入库 */
    private String bizType;

    /** 来源单据 ID（org_transfer.id） */
    private Long bizId;

    /** 来源单号（IT+yyyyMMdd+3位流水） */
    private String bizNo;

    /** 仓库 ID */
    private Long warehouseId;

    /** 物料 ID */
    private Long materialId;

    /** 数量（正入负出） */
    private BigDecimal qty;

    /** 备注（记 IT 单号） */
    private String remark;
}
