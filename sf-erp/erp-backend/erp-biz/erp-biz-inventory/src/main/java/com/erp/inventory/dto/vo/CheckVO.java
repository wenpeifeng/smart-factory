package com.erp.inventory.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 盘点单列表行 VO。
 */
@Data
public class CheckVO {

    private Long id;

    /** 单号（CK...） */
    private String no;

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;

    /** 状态：0盘点中 1已完成 */
    private Integer status;

    /** 来源：MANUAL 手工 / CYCLE 循环盘点自动（V421） */
    private String source;

    /** 完成时间 */
    private LocalDateTime completeTime;

    /** 备注 */
    private String remark;

    /** 明细行数 */
    private Long lineCount;

    /** 已录实盘行数 */
    private Long actualCount;

    /** 存在差异的行数（diff_qty != 0） */
    private Long diffCount;

    /** 创建时间 */
    private LocalDateTime createTime;
}
