package com.mes.quality.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 检验模板项目实体（表 qc_template_item，org_id=0 共享）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("qc_template_item")
public class QcTemplateItem extends BaseEntity {
    /** 模板 ID */
    private Long templateId;
    /** 检验项目名称 */
    private String itemName;
    /** 标准值（如 10.00±0.10） */
    private String standardValue;
    /** 下限 */
    private BigDecimal lowerLimit;
    /** 上限 */
    private BigDecimal upperLimit;
    /** 检验方法 */
    private String inspectMethod;
    /** 抽检比例 %（0-100） */
    private BigDecimal sampleRate;
    /** 排序号 */
    private Integer seq;
    /** 备注 */
    private String remark;
}
