package com.erp.hr.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 计件价目 —— 物料 × 工序 的计件单价，计件工资归集时按物料取首条启用价目。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_piece_price")
public class HrPiecePrice extends BaseEntity implements Serializable {

    /** 物料ID（base_material.id） */
    private Long materialId;

    /** 工序名称（如 冲压/焊接/装配） */
    private String processName;

    /** 计件单价（元/件） */
    private BigDecimal unitPrice;

    /** 状态：ENABLED启用 DISABLED停用 */
    private String status;

    /** 备注 */
    private String remark;
}
