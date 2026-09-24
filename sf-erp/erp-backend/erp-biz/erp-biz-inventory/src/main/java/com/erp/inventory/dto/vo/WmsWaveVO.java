package com.erp.inventory.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * WMS 波次行 VO（波次列表，含波内任务进度统计）。
 */
@Data
public class WmsWaveVO {

    private Long id;

    /** 波次号（W+日期+流水） */
    private String waveNo;

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;

    /** 状态：0建波 10拣选中 20完成 */
    private Integer status;

    /** 波内任务总数 */
    private Long taskCount;

    /** 波内已完成任务数 */
    private Long doneCount;

    /** 波内任务总数量 */
    private java.math.BigDecimal totalQty;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
