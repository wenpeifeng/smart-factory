package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 工艺路线头（成品物料 + 版本号）。
 * <p>
 * 关键约束：uk(material_id, version) 数据库唯一；
 * 仅 status = ACTIVE 的工艺路线参与生产订单下达时的工序任务生成（取最新一条）。
 * 状态机与 BOM 一致：DRAFT → ACTIVE → DISABLED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_routing")
public class MrpRouting extends BaseEntity implements Serializable {

    /** 成品物料ID(base_material.id) */
    private Long materialId;

    /** 工艺路线版本号（如 V1.0） */
    private String version;

    /** 状态：DRAFT草稿 ACTIVE已审核(有效) DISABLED停用 */
    private String status;

    /** 备注 */
    private String remark;
}
