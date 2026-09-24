package com.mes.quality.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 检验单项目实体（表 qc_inspect_item，从模板带出后录入实测值）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("qc_inspect_item")
public class QcInspectItem extends BaseEntity {
    /** 检验单 ID */
    private Long inspectId;
    /** 检验项目名称 */
    private String itemName;
    /** 标准值 */
    private String standardValue;
    /** 下限 */
    private BigDecimal lowerLimit;
    /** 上限 */
    private BigDecimal upperLimit;
    /** 检验方法 */
    private String inspectMethod;
    /** 抽检比例 % */
    private BigDecimal sampleRate;
    /** 实测值 */
    private String actualValue;
    /** 行结论：1 合格 / 0 不合格 / NULL 未录入 */
    private Integer lineResult;
    /** 排序号 */
    private Integer seq;
    /** 备注 */
    private String remark;
}
