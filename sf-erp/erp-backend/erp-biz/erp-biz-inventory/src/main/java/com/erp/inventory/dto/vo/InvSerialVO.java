package com.erp.inventory.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 序列号台账行 VO（V422）。
 */
@Data
public class InvSerialVO {

    private Long id;

    /** 序列号 */
    private String serialNo;

    private Long materialId;
    private String materialCode;
    private String materialName;

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;

    /** 批次号（可空串） */
    private String batchNo;

    /** 状态：IN_STOCK在库 OUT已出库 SCRAP报废 */
    private String status;

    /** 入库来源单号 */
    private String sourceNo;

    /** 出库单号 */
    private String outNo;

    /** 备注 */
    private String remark;

    /** 入库登记时间 */
    private LocalDateTime createTime;

    /** 最后变动时间 */
    private LocalDateTime updateTime;
}
