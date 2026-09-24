package com.erp.qc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 检验模板主表实体（qc_template）。
 * <p>
 * 模板 = 检验类型(IQC/IPQC/FQC/OQC) + 适用物料分类 + 检验项目集合（子表 {@link QcTemplateItem}）。
 * category_id 为空表示通用模板；生成检验单时精确分类优先、通用兜底。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_template")
public class QcTemplate extends BaseEntity {

    /** 检验类型：来料检验 */
    public static final String TYPE_IQC = "IQC";
    /** 检验类型：过程检验 */
    public static final String TYPE_IPQC = "IPQC";
    /** 检验类型：成品完工检验 */
    public static final String TYPE_FQC = "FQC";
    /** 检验类型：出货检验 */
    public static final String TYPE_OQC = "OQC";

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;

    /** 模板编码（全局唯一） */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 适用物料分类 ID（base_material_category.id，空=全部分类通用） */
    private Long categoryId;

    /** 检验类型：IQC/IPQC/FQC/OQC */
    private String inspectType;

    /** 状态：1启用 0停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间（MP 填充，列表展示用） */
    private LocalDateTime createTime;
}
