package com.erp.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * WMS 库内作业 KPI VO（V421，tasks 页顶部卡片）。
 * <p>
 * 统计窗口 [from, to]（默认近 30 天）：
 * <ul>
 *   <li>completedTasks：完成任务数（status=30 且完成时间在窗口内）；</li>
 *   <li>avgDurationMinutes：任务平均耗时（完成时间-创建时间，分钟）；</li>
 *   <li>onTimeRate：及时率（创建当日完成的任务占完成总数比例，0~1）；</li>
 *   <li>stockAccuracy：库存准确率（1 - 盘点差异行/盘点行，窗口内完成的盘点单，0~1；无盘点返回 null）；</li>
 *   <li>byAssignee：人效明细（执行人完成任务数×均耗时）。</li>
 * </ul>
 */
@Data
public class WmsKpiVO {

    /** 完成任务量 */
    private Long completedTasks;

    /** 平均耗时（分钟） */
    private BigDecimal avgDurationMinutes;

    /** 及时率（当日完成占比，0~1） */
    private BigDecimal onTimeRate;

    /** 库存准确率（1-差异行/盘点行，0~1；窗口内无盘点为 null） */
    private BigDecimal stockAccuracy;

    /** 各状态任务存量（看板：{status: cnt}） */
    private List<StatusCount> statusCounts;

    /** 人效明细（按执行人） */
    private List<AssigneeKpi> byAssignee;

    /** 状态计数 */
    @Data
    public static class StatusCount {
        private Integer status;
        private Long cnt;
    }

    /** 执行人效率 */
    @Data
    public static class AssigneeKpi {
        private Long assignee;
        private String assigneeName;
        /** 完成任务数 */
        private Long completed;
        /** 平均耗时（分钟） */
        private BigDecimal avgDurationMinutes;
    }
}
