package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * BOM 物料清单头（成品物料 + 版本号）。
 * <p>
 * 关键约束：同一成品物料下版本号唯一（服务层校验，逻辑删除后版本可复用）；
 * 仅 status = ACTIVE 的 BOM 参与 MRP 展开与生产订单下达校验。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_bom")
public class MrpBom extends BaseEntity implements Serializable {

    /** 成品物料ID(base_material.id) */
    private Long materialId;

    /** BOM版本号（如 V1.0） */
    private String version;

    /** 状态：DRAFT草稿 ACTIVE已审核(有效) DISABLED停用 */
    private String status;

    /** 备注 */
    private String remark;
}
