package com.mes.quality.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * SPC 控制规则实体（表 qc_spc_rule，物料 x 检验项目维度）。
 * <p>判异准则：R1 超限 / R2 连续9点同侧 / R3 连续6点趋势 / R4 连续14点交替 / R5 3点中2点同侧B区外。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("qc_spc_rule")
public class QcSpcRule extends BaseEntity {
    /** 物料 ID */
    private Long materialId;
    /** 检验项目名称（与检验模板项目名对齐） */
    private String itemName;
    /** 控制上限 UCL */
    private BigDecimal ucl;
    /** 控制下限 LCL */
    private BigDecimal lcl;
    /** 目标值/中心线（空=(UCL+LCL)/2） */
    private BigDecimal target;
    /** 准则 R1（超限）开关：1 启用 */
    private Integer ruleR1;
    /** 准则 R2（连续9点同侧）开关 */
    private Integer ruleR2;
    /** 准则 R3（连续6点趋势）开关 */
    private Integer ruleR3;
    /** 准则 R4（连续14点交替）开关，默认停用 */
    private Integer ruleR4;
    /** 准则 R5（3点中2点同侧B区外）开关 */
    private Integer ruleR5;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    /** 备注 */
    private String remark;
}
