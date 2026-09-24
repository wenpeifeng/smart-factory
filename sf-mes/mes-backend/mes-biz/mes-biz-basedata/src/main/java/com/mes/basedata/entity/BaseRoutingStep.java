package com.mes.basedata.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 工艺路线工序步骤（表 base_routing_step）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("base_routing_step")
public class BaseRoutingStep extends BaseEntity {
    /** 工艺路线 ID（base_routing.id） */
    private Long routingId;
    /** 序号（工序顺序，从 1 起） */
    private Integer seqNo;
    /** 工序 ID（base_operation.id） */
    private Long operationId;
    /** 工作中心 ID（空=取工序默认工作中心） */
    private Long workcenterId;
    /** 单件工时分钟（空=取工序单位加工时间） */
    private BigDecimal unitMinutes;
    /** 是否报工点：1 是 / 0 否 */
    private Integer reportFlag;
    /** 是否关键件采集点：1 过站需扫码关键件 SN */
    private Integer keyComponentFlag;
    /** 备注 */
    private String remark;
}
