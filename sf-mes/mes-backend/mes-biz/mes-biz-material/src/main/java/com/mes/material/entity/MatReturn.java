package com.mes.material.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 退料单实体（表 mat_return）。
 * <p>状态机：DRAFT → AUDITED（审核即生效：线边仓扣减 + 回传 ERP 入库）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("mat_return")
public class MatReturn extends BaseEntity {
    /** 退料单号（TL+yyyyMMdd+4位流水） */
    private String returnNo;
    /** 工单 ID（NULL=非工单余料退库） */
    private Long workorderId;
    /** 工单号（快照） */
    private String woNo;
    /** 退料类型：LEFTOVER 余料退库 / REJECT 退供应商 */
    private String returnType;
    /** 退回仓库 ID */
    private Long warehouseId;
    /** 状态：DRAFT / AUDITED */
    private String status;
    /** 审核时间 */
    private LocalDateTime auditTime;
    /** 审核人 */
    private String auditor;
    /** 备注 */
    private String remark;
    /** ERP 回传标志：0 / 1 */
    private Integer erpPushed;
}
