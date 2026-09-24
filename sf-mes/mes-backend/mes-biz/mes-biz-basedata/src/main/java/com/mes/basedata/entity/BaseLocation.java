package com.mes.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库位实体（表 base_location，仓库下多库位）。
 * <p>
 * 库位编码在仓库内唯一（uk: warehouse_id + code）；
 * 容量供库存模块做库位容量校验。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_location")
public class BaseLocation extends BaseEntity {

    /** 所属仓库 ID（base_warehouse.id） */
    private Long warehouseId;

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
