package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库位实体（表 base_location，仓库下多库位）。
 * <p>
 * 库位编码在仓库内唯一（uk: warehouse_id + code）；
 * 容量供库存模块做库位容量校验。
 * V421 三级升级：+ zone_id（所属库区，空=未分区）+ loc_type（库位用途，默认 STORAGE）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_location")
public class BaseLocation extends BaseEntity {

    /** 所属仓库 ID（base_warehouse.id） */
    private Long warehouseId;

    /** 所属库区 ID（base_zone.id，空=未分区，V421） */
    private Long zoneId;

    /** 库位用途：STORAGE 存储 / PICKING 拣选 / RECEIVE 收货 / SHIP 发货 / DEFECT 不良品（V421） */
    private String locType;

    /** 库位编码，仓库内唯一 */
    private String code;

    /** 库位名称 */
    private String name;

    /** 容量，DECIMAL(18,4) */
    private BigDecimal capacity;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
