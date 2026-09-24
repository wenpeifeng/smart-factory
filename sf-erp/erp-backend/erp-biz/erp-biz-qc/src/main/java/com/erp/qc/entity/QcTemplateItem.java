package com.erp.qc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 检验模板明细实体（qc_template_item）—— 模板行即检验项目。
 * <p>
 * 上下限均空 = 定性项目（如外观目测），不参与数值比较，仅人工判定。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_template_item")
public class QcTemplateItem extends BaseEntity {

    /** 模板 ID（qc_template.id） */
    private Long templateId;

    /** 检验项目名称 */
    private String itemName;

    /** 标准值（文本或数值，如 10.00±0.10） */
    private String standardValue;

    /** 标准下限（空=不限） */
    private BigDecimal lowerLimit;

    /** 标准上限（空=不限） */
    private BigDecimal upperLimit;

    /** 检验方法（如目测/千分尺/耐压测试） */
    private String inspectMethod;

    /** 抽检比例 %（0-100） */
    private BigDecimal sampleRate;

    /** 排序（小在前） */
    private Integer seq;

    /** 备注 */
    private String remark;
}
