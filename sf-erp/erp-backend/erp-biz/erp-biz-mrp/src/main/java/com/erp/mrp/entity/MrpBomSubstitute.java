package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * BOM 子件替代组行（V621）—— 主料 = mrp_bom_item 行自身物料，
 * 本表描述"主料不足时按优先级依次补足的替代料"。
 * <p>
 * 换算口径：1 单位主料 = ratio 单位替代料（ratio 默认 1）。
 * 倒冲链路使用；MRP 运算暂不参与（见 docs/modules/mrp.md 三期章节）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_bom_substitute")
public class MrpBomSubstitute extends BaseEntity implements Serializable {

    /** BOM 子件行ID（mrp_bom_item.id，主料即该行物料） */
    private Long bomItemId;

    /** 替代料物料ID（base_material.id） */
    private Long subMaterialId;

    /** 优先级（1 最高，主料不足时按升序依次补足） */
    private Integer priority;

    /** 用量换算比（1 单位主料 = ratio 单位替代料） */
    private BigDecimal ratio;

    /** 备注 */
    private String remark;
}
