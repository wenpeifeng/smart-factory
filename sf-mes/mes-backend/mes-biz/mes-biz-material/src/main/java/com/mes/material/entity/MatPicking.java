package com.mes.material.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 领料单实体（表 mat_picking）。
 * <p>状态机：DRAFT → AUDITED（审核即生效：线边仓入库 + 回写工单已领 + 回传 ERP）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("mat_picking")
public class MatPicking extends BaseEntity {
    /** 领料单号（LL+yyyyMMdd+4位流水） */
    private String pickNo;
    /** 工单 ID（plan_workorder.id） */
    private Long workorderId;
    /** 工单号（快照） */
    private String woNo;
    /** 发料仓库 ID（base_warehouse.id，回传 ERP 用） */
    private Long warehouseId;
    /** 状态：DRAFT 草稿 / AUDITED 已审核 */
    private String status;
    /** 审核时间 */
    private LocalDateTime auditTime;
    /** 审核人 */
    private String auditor;
    /** 领料类型：NORMAL 正常 / URGENT 加急 */
    private String pickType;
    /** 备注 */
    private String remark;
    /** ERP 回传标志：0 未回传 / 1 已回传 */
    private Integer erpPushed;
}
