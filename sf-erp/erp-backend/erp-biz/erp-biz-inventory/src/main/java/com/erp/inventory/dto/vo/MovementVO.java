package com.erp.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水行 VO。
 */
@Data
public class MovementVO {

    private Long id;

    /** 业务类型编码（PUR_IN/OTH_OUT/...） */
    private String bizType;

    /** 业务单据 ID */
    private Long bizId;

    /** 业务单号 */
    private String bizNo;

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;

    private Long materialId;
    private String materialCode;
    private String materialName;

    /** 批次号（FIFO 拆批出库时每个批次一行流水；非批次物料/一期历史流水为空串） */
    private String batchNo;

    /** 库位 ID（0=未定位/汇总行，V421） */
    private Long locationId;

    /** 库位编码（location_id=0 时为 null） */
    private String locationCode;

    /** 变动数量（正入负出） */
    private BigDecimal qty;

    /** 备注 */
    private String remark;

    /** 操作时间 */
    private LocalDateTime createTime;
}
