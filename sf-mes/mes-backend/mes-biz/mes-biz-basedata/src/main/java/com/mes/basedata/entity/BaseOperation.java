package com.mes.basedata.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 标准工序库（表 base_operation）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("base_operation")
public class BaseOperation extends BaseEntity {
    /** 工序编码（OP+yyyyMMdd+4位流水） */
    private String code;
    /** 工序名称 */
    private String name;
    /** 默认工作中心 ID（base_workcenter.id） */
    private Long defaultWorkcenterId;
    /** 准备时间（分钟/批） */
    private Integer setupMinutes;
    /** 单位加工时间（分钟/件） */
    private BigDecimal unitMinutes;
    /** 外协标志：1 外协 / 0 自制 */
    private Integer outsourceFlag;
    /** 是否质检点：1 是（IPQC 挂点）/ 0 否 */
    private Integer qcFlag;
    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private String status;
    /** 备注 */
    private String remark;
}
