package com.mes.plan.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 齐套分析快照头实体（表 plan_kit_check）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("plan_kit_check")
public class PlanKitCheck extends BaseEntity {
    /** 分析单号（QT+yyyyMMdd+4位流水） */
    private String checkNo;
    /** 工单 ID（plan_workorder.id） */
    private Long workorderId;
    /** 工单号（快照） */
    private String woNo;
    /** 分析结果：FULL 齐套 / SHORT 缺料 / PART 部分齐套 */
    private String result;
    /** 缺料行数 */
    private Integer shortCount;
}
