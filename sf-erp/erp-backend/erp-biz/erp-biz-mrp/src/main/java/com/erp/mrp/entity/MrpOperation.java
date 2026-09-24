package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工序定义 —— 标准工序库，供工艺路线步骤引用。
 * <p>
 * 关键约束：编码唯一（OP+流水）；setup_minutes 为整批准备时间，
 * unit_minutes 为单件加工时间（工艺路线步骤未覆盖时取该默认值）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_operation")
public class MrpOperation extends BaseEntity implements Serializable {

    /** 编码（OP+yyyyMMdd+4位流水） */
    private String code;

    /** 工序名称 */
    private String name;

    /** 默认工作中心ID(mrp_work_center.id) */
    private Long defaultWorkCenterId;

    /** 准备时间（分钟/批） */
    private Integer setupMinutes;

    /** 单位加工时间（分钟/每件） */
    private BigDecimal unitMinutes;

    /** 外协标志：1外协 0自制 */
    private Integer outsourceFlag;

    /** 状态：ENABLED启用 DISABLED停用 */
    private String status;

    /** 备注 */
    private String remark;
}
