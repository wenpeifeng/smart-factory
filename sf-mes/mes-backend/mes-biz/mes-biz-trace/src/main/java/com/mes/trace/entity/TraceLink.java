package com.mes.trace.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 追溯关系实体（表 trace_link）：消耗对象（来料批次/关键件SN）→ 产品批次/SN x 工序。
 * <p>数据来源：领料审核事件（批次上料）与过站报工事件（关键件 SN）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("trace_link")
public class TraceLink extends BaseEntity {
    /** 产品批次号/SN（产出对象） */
    private String productNo;
    /** 工单 ID（plan_workorder.id） */
    private Long workorderId;
    /** 工序任务 ID（消耗发生工序，上料类为空） */
    private Long woStepId;
    /** 消耗对象编号（来料批次/关键件 SN） */
    private String componentNo;
    /** 消耗对象类型：BATCH 批次 / SN 序列号 */
    private String componentType;
    /** 消耗物料 ID（快照） */
    private Long materialId;
    /** 消耗数量（SN 类为 1） */
    private BigDecimal qty;
    /** 关联时间（过站/领料时间） */
    private LocalDateTime linkTime;
}
