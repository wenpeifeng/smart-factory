package com.erp.inventory.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘点单详情 VO（头 + 明细快照行）。
 */
@Data
public class CheckDocDetailVO {

    private Long id;

    /** 单号（CK...） */
    private String no;

    private Long warehouseId;

    /** 状态：0盘点中 1已完成 */
    private Integer status;

    /** 来源：MANUAL 手工 / CYCLE 循环盘点自动（V421） */
    private String source;

    /** 完成时间 */
    private LocalDateTime completeTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 明细行（快照 + 实盘 + 差异） */
    private List<CheckDetailVO> lines;
}
