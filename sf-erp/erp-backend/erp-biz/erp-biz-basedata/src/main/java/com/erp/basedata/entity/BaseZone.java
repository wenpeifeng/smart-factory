package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库区实体（表 base_zone，V421）—— 仓库→库区→库位三级的中间层。
 * <p>
 * 库区编码在仓库内唯一（uk: warehouse_id + code）；
 * zone_type 决定库区用途（收货/存储/拣选/发货/不良品），
 * WMS 上架推荐库位时按 STORAGE 区空容库位推荐。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_zone")
public class BaseZone extends BaseEntity {

    /** 所属仓库 ID（base_warehouse.id） */
    private Long warehouseId;

    /** 库区编码，仓库内唯一 */
    private String code;

    /** 库区名称 */
    private String name;

    /** 库区类型：RECEIVE 收货 / STORAGE 存储 / PICKING 拣选 / SHIP 发货 / DEFECT 不良品 */
    private String zoneType;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
