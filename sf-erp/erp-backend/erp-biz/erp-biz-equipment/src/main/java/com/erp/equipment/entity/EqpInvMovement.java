package com.erp.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库存流水 —— 设备模块视角的 inv_movement 只写映射。
 * <p>
 * 跨模块约定（docs/modules/inventory.md）：备件出入库由本模块按约定表结构直接 INSERT
 * （biz_type 用 {@link com.erp.equipment.constant.EqpBizType} 的 EQP_OUT / EQP_IN），
 * 不 import erp-inventory 代码。字段语义与库存模块的 InvMovement 完全一致：qty 正入负出。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_movement")
public class EqpInvMovement extends BaseEntity {

    /** 业务类型（EQP_OUT 备件出库 / EQP_IN 备件退回） */
    private String bizType;

    /** 业务单据 ID（维修工单 id） */
    private Long bizId;

    /** 业务单号（维修工单号 WX...） */
    private String bizNo;

    /** 仓库 ID */
    private Long warehouseId;

    /** 物料 ID */
    private Long materialId;

    /** 变动数量：正数=入库，负数=出库 */
    private BigDecimal qty;

    /** 备注 */
    private String remark;
}
