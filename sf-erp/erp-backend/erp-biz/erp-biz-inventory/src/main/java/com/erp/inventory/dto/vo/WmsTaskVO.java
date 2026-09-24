package com.erp.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * WMS 作业任务行 VO（任务列表 / 波次任务列表共用；波次列表按库位编码排序=拣选路径序）。
 */
@Data
public class WmsTaskVO {

    private Long id;

    /** 任务号（T+日期+流水） */
    private String taskNo;

    /** 任务类型：PUTAWAY/PICK/MOVE/REPLENISH */
    private String taskType;

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;

    private Long materialId;
    private String materialCode;
    private String materialName;

    /** 批次号（非批次物料空串） */
    private String batchNo;

    /** 任务数量 */
    private BigDecimal qty;

    /** 目标/来源库位 ID（可空=未推荐到） */
    private Long locationId;

    /** 库位编码（推荐/指定库位；空=未定位） */
    private String locationCode;

    /** 调出库位 ID（MOVE/REPLENISH） */
    private Long fromLocationId;

    /** 调出库位编码 */
    private String fromLocationCode;

    /** 所属波次 ID */
    private Long waveId;

    /** 波次号 */
    private String waveNo;

    /** 来源单据类型 */
    private String sourceType;

    /** 来源单号 */
    private String sourceNo;

    /** 执行人用户 ID */
    private Long assignee;

    /** 执行人昵称/用户名（sys_user 冗余展示；用户缺失为 null） */
    private String assigneeName;

    /** 状态：0待分配 10待执行 20执行中 30完成 40取消 */
    private Integer status;

    /** 推荐库位理由 */
    private String recommendReason;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
