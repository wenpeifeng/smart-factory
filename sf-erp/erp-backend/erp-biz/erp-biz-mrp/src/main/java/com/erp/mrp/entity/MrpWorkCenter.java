package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工作中心 —— 车间作业管理与能力计划(CRP)的产能计量单元。
 * <p>
 * 关键约束：编码唯一（WC+流水，服务层生成）；每日产能工时是 CRP 可用工时的
 * 计算基数（可用工时 = 每日产能 × 工作日数，周末不计）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_work_center")
public class MrpWorkCenter extends BaseEntity implements Serializable {

    /** 编码（WC+yyyyMMdd+4位流水） */
    private String code;

    /** 工作中心名称 */
    private String name;

    /** 所属车间（文本） */
    private String workshop;

    /** 每日产能工时（小时/天） */
    private BigDecimal capacityHoursPerDay;

    /** 设备数 */
    private Integer equipmentCount;

    /** 状态：ENABLED启用 DISABLED停用 */
    private String status;

    /** 备注 */
    private String remark;
}
