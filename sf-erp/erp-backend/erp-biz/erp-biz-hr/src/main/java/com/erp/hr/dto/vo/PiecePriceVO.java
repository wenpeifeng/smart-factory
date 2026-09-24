package com.erp.hr.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计件价目 VO（带物料编码/名称回显）。
 */
@Data
public class PiecePriceVO {

    private Long id;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 工序名称 */
    private String processName;

    /** 计件单价（元/件） */
    private BigDecimal unitPrice;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
