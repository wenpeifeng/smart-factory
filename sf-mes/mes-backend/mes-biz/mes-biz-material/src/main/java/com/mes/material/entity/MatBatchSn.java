package com.mes.material.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 批次/SN 档案实体（表 mat_batch_sn，obj_type 区分批次与单件 SN）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("mat_batch_sn")
public class MatBatchSn extends BaseEntity {
    /** 对象类型：BATCH 批次 / SN 单件序列号 */
    private String objType;
    /** 批次号或 SN */
    private String objNo;
    /** 物料 ID */
    private Long materialId;
    /** 来源类型：PICKING 领料 / PRODUCE 生产产出 / PURCHASE 采购到货 */
    private String sourceType;
    /** 来源单号 */
    private String sourceNo;
    /** 工单 ID（生产产出） */
    private Long workorderId;
    /** 数量（批次为批量，SN 为 1） */
    private BigDecimal qty;
    /** 供应商 ID（来料批次） */
    private Long supplierId;
    /** 状态：IN_STOCK 在库 / CONSUMED 已消耗 / SHIPPED 已出货 */
    private String status;
    /** 父级 SN（BOM 级追溯：成品 SN -> 关键件 SN） */
    private String parentSn;
}
