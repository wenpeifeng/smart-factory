package com.erp.cost.dto.raw;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * inv_movement 只读投影（sync 重放数据源）。
 * <p>
 * 仅供 XML resultType 映射，不对前端暴露。
 */
@Data
public class MovementRaw {

    /** inv_movement.id */
    private Long id;

    /** 业务类型编码 */
    private String bizType;

    /** 来源单据 ID */
    private Long bizId;

    /** 来源单号 */
    private String bizNo;

    /** 仓库 ID */
    private Long warehouseId;

    /** 物料 ID */
    private Long materialId;

    /** 变动数量（正入负出） */
    private BigDecimal qty;

    /** 流水时间（期间与排序依据） */
    private LocalDateTime moveTime;

    /** 期间 yyyyMM（SQL 由 DATE_FORMAT 生成） */
    private String period;
}
