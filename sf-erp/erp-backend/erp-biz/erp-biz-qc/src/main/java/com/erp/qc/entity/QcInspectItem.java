package com.erp.qc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 检验单明细实体（qc_inspect_item）—— 检验项目来自模板快照，可补录实检值与单项判定。
 * <p>
 * 快照设计：检验单生成时把模板行复制为本表行，模板后续修改/删除不影响历史检验单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_inspect_item")
public class QcInspectItem extends BaseEntity {

    /** 单项判定：合格 */
    public static final int LINE_PASS = 1;
    /** 单项判定：不合格 */
    public static final int LINE_FAIL = 0;

    /** 检验单 ID（qc_inspect.id） */
    private Long inspectId;

    /** 检验项目名称（模板快照） */
    private String itemName;

    /** 标准值（模板快照） */
    private String standardValue;

    /** 标准下限（模板快照） */
    private BigDecimal lowerLimit;

    /** 标准上限（模板快照） */
    private BigDecimal upperLimit;

    /** 检验方法（模板快照） */
    private String inspectMethod;

    /** 抽检比例 %（模板快照） */
    private BigDecimal sampleRate;

    /** 实检值（文本/数值均可，提交检验时录入） */
    private String actualValue;

    /** 单项判定：1合格 0不合格 空=未判定 */
    private Integer lineResult;

    /** 排序（小在前） */
    private Integer seq;

    /** 备注 */
    private String remark;
}
