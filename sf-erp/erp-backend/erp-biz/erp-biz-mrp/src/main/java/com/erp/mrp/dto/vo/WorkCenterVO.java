package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工作中心 VO。
 */
@Data
public class WorkCenterVO implements Serializable {

    /** 工作中心ID */
    private Long id;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 所属车间 */
    private String workshop;

    /** 每日产能工时（小时/天） */
    private BigDecimal capacityHoursPerDay;

    /** 设备数 */
    private Integer equipmentCount;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
