package com.mes.quality.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPC 样本实体（表 qc_spc_sample，检验单提交时自动采集数值型实检值）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("qc_spc_sample")
public class QcSpcSample extends BaseEntity {
    /** 规则 ID（qc_spc_rule.id） */
    private Long ruleId;
    /** 来源检验单 ID */
    private Long inspectId;
    /** 样本值（数值型实检值） */
    private BigDecimal value;
    /** 采样时间（检验提交时间） */
    private LocalDateTime sampleTime;
    /** 命中的判异准则（逗号分隔，如 R1,R3；空=正常） */
    private String violations;
}
