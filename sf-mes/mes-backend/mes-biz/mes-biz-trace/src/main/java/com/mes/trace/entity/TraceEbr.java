package com.mes.trace.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 电子批记录实体（表 trace_ebr）：工单完工时生成的全数据快照（JSON 载荷，只读）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("trace_ebr")
public class TraceEbr extends BaseEntity {
    /** 批记录编号（EBR+yyyyMMdd+4位流水） */
    private String ebrNo;
    /** 工单 ID */
    private Long workorderId;
    /** 工单号（快照） */
    private String woNo;
    /** 成品物料 ID */
    private Long materialId;
    /** 产出批次号 */
    private String batchNo;
    /** 批记录内容 JSON（工艺/过站明细/用料批次/质检结果/人员设备） */
    private String content;
    /** 生成时间 */
    private LocalDateTime generateTime;
}
