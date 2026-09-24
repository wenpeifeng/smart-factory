package com.mes.report.dto.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 车间电子看板大屏 VO（一次拉全，前端 30 秒轮询）。
 */
@Data
public class ScreenVO {
    /** 未关闭安灯：type -> cnt */
    private List<Map<String, Object>> andons;
    /** 工单状态分布 */
    private List<Map<String, Object>> woStatus;
    /** 工序在制 Top10 */
    private List<Map<String, Object>> wipTop;
    /** 工单进度榜 Top10 */
    private List<Map<String, Object>> woProgress;
    /** 近 7 天产量曲线 */
    private List<Map<String, Object>> dailyOutput;
    /** 检验汇总 */
    private List<Map<String, Object>> quality;
    /** 未关闭安灯总数 */
    private Integer openAndonCount;
}
