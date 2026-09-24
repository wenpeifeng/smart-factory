package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工作中心负荷聚合结果（CRP Mapper 投影行）：
 * 负荷分钟数 = SUM(未完工工序任务 计划数量 × 单件工时)。
 */
@Data
public class WorkCenterLoadVO implements Serializable {

    /** 工作中心ID */
    private Long workCenterId;

    /** 负荷工时（分钟） */
    private BigDecimal loadMinutes;
}
