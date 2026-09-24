package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 派工报工看板 VO —— 按工作中心分组的工序任务卡片（未分组任务单独一组）。
 */
@Data
public class DispatchBoardVO implements Serializable {

    /** 看板分组（按工作中心升序，未指定工作中心的任务归"未分组"放最后） */
    private List<Group> groups = new ArrayList<>();

    /** 分组：一个工作中心的任务集合 */
    @Data
    public static class Group implements Serializable {

        /** 工作中心ID（未分组为 null） */
        private Long workCenterId;

        /** 工作中心编码 */
        private String workCenterCode;

        /** 工作中心名称 */
        private String workCenterName;

        /** 每日产能工时（信息展示） */
        private BigDecimal capacityHoursPerDay;

        /** 该组任务数 */
        private Integer stepCount;

        /** 任务卡片 */
        private List<WoStepVO> steps = new ArrayList<>();
    }
}
