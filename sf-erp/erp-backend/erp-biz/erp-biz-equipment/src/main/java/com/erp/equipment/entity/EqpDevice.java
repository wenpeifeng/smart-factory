package com.erp.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备台账实体 —— 设备管理的主数据。
 * <p>
 * 状态机（changeStatus 接口按矩阵校验，见 EqpDeviceServiceImpl）：
 * 1 运行 ↔ 2 停机 ↔ 3 维修中 三态互通（维修工单接单/完工会联动设备状态），
 * 4 报废为单向终态，任何状态可转入、转入后不可再变更。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eqp_device")
public class EqpDevice extends BaseEntity {

    /** 状态：运行 */
    public static final int STATUS_RUNNING = 1;
    /** 状态：停机 */
    public static final int STATUS_STOPPED = 2;
    /** 状态：维修中 */
    public static final int STATUS_REPAIRING = 3;
    /** 状态：报废（终态） */
    public static final int STATUS_SCRAPPED = 4;

    /** 设备编号（SB+yyyyMMdd+4位流水，表单留空时自动生成） */
    private String code;

    /** 设备名称 */
    private String name;

    /** 规格型号 */
    private String model;

    /** 制造商 */
    private String manufacturer;

    /** 购置日期 */
    private LocalDate purchaseDate;

    /** 购置原值（DECIMAL(18,2)） */
    private BigDecimal originalValue;

    /** 所在车间（文本，不关联组织表） */
    private String workshop;

    /** 设备状态：1运行 2停机 3维修中 4报废 */
    private Integer status;

    /** 责任人（文本） */
    private String responsiblePerson;

    /** 点检周期（天），空表示不点检 */
    private Integer checkPeriodDays;

    /** 备注 */
    private String remark;
}
