package com.mes.material.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 线边仓库存实体（表 mat_shop_stock，物料+批次+工作中心维度）。
 * <p>增减一律条件 UPDATE（stock = stock ± ?），防止并发超扣。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("mat_shop_stock")
public class MatShopStock extends BaseEntity {
    /** 物料 ID */
    private Long materialId;
    /** 批次号（批次不管理物料为空串） */
    private String batchNo;
    /** 工作中心 ID */
    private Long workcenterId;
    /** 库存数量 */
    private BigDecimal qty;
}
