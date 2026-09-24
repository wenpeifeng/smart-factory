package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库存流水 —— 每一次库存变动落一条，正入负出，是库存审计的唯一来源。
 * <p>
 * 与业务单据的关系：biz_type + biz_id + biz_no 定位来源单据
 * （如 OTH_IN + 其他入库单id + OI202609050001）。
 * 采购/销售/生产模块按本表约定结构直接写入；本模块经
 * {@link com.erp.inventory.service.StockService} 统一写入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_movement")
public class InvMovement extends BaseEntity {

    /** 业务类型（取值见 {@link com.erp.inventory.constant.InvBizType}） */
    private String bizType;

    /** 业务单据 ID（无单据的裸操作可为空） */
    private Long bizId;

    /** 业务单号（如 OI202609050001 / CK202609050001 / TR202609050001） */
    private String bizNo;

    /** 仓库 ID */
    private Long warehouseId;

    /** 库位 ID（0=未定位/汇总行；&gt;0=实际作业库位，V421） */
    private Long locationId;

    /** 物料 ID */
    private Long materialId;

    /** 批次号（V411 起：FIFO 拆批出库时每个批次一行流水；非批次物料/一期历史流水为空串） */
    private String batchNo;

    /** 变动数量：正数=入库，负数=出库 */
    private BigDecimal qty;

    /** 备注 */
    private String remark;
}
