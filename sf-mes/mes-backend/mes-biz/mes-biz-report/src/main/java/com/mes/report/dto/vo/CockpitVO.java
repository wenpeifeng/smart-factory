package com.mes.report.dto.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 管理驾驶舱 VO（管理视角核心指标一次拉全）。
 */
@Data
public class CockpitVO {
    /** 工单状态分布（含总数） */
    private List<Map<String, Object>> woStatus;
    /** 近 14 天产量（合格/报废） */
    private List<Map<String, Object>> dailyOutput;
    /** 检验汇总（IQC/IPQC/FQC） */
    private List<Map<String, Object>> quality;
    /** 缺料工单数 */
    private Integer shortWorkorders;
    /** 回传积压数 */
    private Integer pendingPushes;
    /** 未关闭安灯数 */
    private Integer openAndons;
}
