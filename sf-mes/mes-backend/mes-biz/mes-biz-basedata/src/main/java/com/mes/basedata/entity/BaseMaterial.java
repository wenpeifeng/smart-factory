package com.mes.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 物料实体（表 base_material）。
 * <p>
 * 编码唯一（规则 WL+yyyyMMdd+3位流水，可手工指定）；
 * 金额 refCost 为 DECIMAL(18,2)，数量 safetyStock 为 DECIMAL(18,4)；
 * 批次管理/序列号管理开关由库存模块出入库单据读取，决定是否强制批次号/序列号录入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_material")
public class BaseMaterial extends BaseEntity {

    /** 物料编码，全局唯一（自动生成规则：WL+日期+3位流水） */
    private String code;

    /** 物料名称 */
    private String name;

    /** 规格型号 */
    private String spec;

    /** 物料分类 ID（base_material_category.id） */
    private Long categoryId;

    /** 计量单位 ID（base_unit.id） */
    private Long unitId;

    /** 条码 */
    private String barcode;

    /** 是否批次管理：1-是 0-否 */
    private Integer batchManaged;

    /** 是否序列号管理：1-是 0-否 */
    private Integer serialManaged;

    /** 安全库存，DECIMAL(18,4) */
    private BigDecimal safetyStock;

    /** 参考成本价，DECIMAL(18,2) */
    private BigDecimal refCost;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
