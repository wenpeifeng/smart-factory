package com.mes.basedata.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * BOM 物料清单头（表 base_bom）。
 * <p>成品物料 + 版本唯一；status：DRAFT/ACTIVE/DISABLED，
 * 工单下达时取 ACTIVE 版本展开用料。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("base_bom")
public class BaseBom extends BaseEntity {
    /** 成品物料 ID（base_material.id） */
    private Long materialId;
    /** BOM 版本号（如 V1.0） */
    private String version;
    /** 状态：DRAFT 草稿 / ACTIVE 已审核(有效) / DISABLED 停用 */
    private String status;
    /** 备注 */
    private String remark;
}
