package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工序定义 VO（含默认工作中心回显）。
 */
@Data
public class OperationVO implements Serializable {

    /** 工序ID */
    private Long id;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 默认工作中心ID */
    private Long defaultWorkCenterId;

    /** 默认工作中心编码 */
    private String defaultWorkCenterCode;

    /** 默认工作中心名称 */
    private String defaultWorkCenterName;

    /** 准备时间（分钟/批） */
    private Integer setupMinutes;

    /** 单位加工时间（分钟/每件） */
    private BigDecimal unitMinutes;

    /** 外协标志：1外协 0自制 */
    private Integer outsourceFlag;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
