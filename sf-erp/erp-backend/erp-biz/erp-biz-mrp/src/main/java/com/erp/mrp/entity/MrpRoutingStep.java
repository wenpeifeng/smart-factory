package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工艺路线工序步骤 —— 一行代表工艺序列中的一道工序。
 * <p>
 * 关键约束：seq_no 在同一工艺路线内唯一且连续（服务层校验）；
 * work_center_id / unit_minutes 允许覆盖工序默认值（空 = 取工序默认）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_routing_step")
public class MrpRoutingStep extends BaseEntity implements Serializable {

    /** 工艺路线ID(mrp_routing.id) */
    private Long routingId;

    /** 序号（工序顺序，从1起） */
    private Integer seqNo;

    /** 工序ID(mrp_operation.id) */
    private Long operationId;

    /** 工作中心ID（空=取工序默认工作中心） */
    private Long workCenterId;

    /** 单件工时分钟（空=取工序单位加工时间） */
    private BigDecimal unitMinutes;

    /** 是否报工点：1是 0否 */
    private Integer reportFlag;

    /** 备注 */
    private String remark;
}
