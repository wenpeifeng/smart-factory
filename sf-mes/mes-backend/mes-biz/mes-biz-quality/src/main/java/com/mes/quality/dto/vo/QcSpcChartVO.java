package com.mes.quality.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SPC 控制图数据 VO（前端 Recharts 渲染）。
 */
@Data
public class QcSpcChartVO {
    /** 规则信息 */
    private QcSpcRuleVO rule;
    /** 中心线（target 或 (UCL+LCL)/2） */
    private BigDecimal center;
    /** 最近样本（时间升序，含判异标记） */
    private List<SampleVO> samples;

    /** 样本点 */
    @Data
    public static class SampleVO {
        private Long id;
        private BigDecimal value;
        private LocalDateTime sampleTime;
        private Long inspectId;
        /** 命中准则（空=正常） */
        private String violations;
    }
}
