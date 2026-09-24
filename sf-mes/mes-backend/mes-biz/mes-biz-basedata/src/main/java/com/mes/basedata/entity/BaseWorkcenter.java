package com.mes.basedata.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 工作中心（表 base_workcenter，排程/OEE 的资源单元）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("base_workcenter")
public class BaseWorkcenter extends BaseEntity {
    /** 工作中心编码（WC+yyyyMMdd+4位流水） */
    private String code;
    /** 工作中心名称 */
    private String name;
    /** 所属车间（文本） */
    private String workshop;
    /** 每日产能工时（小时/天） */
    private BigDecimal capacityHoursPerDay;
    /** 设备数 */
    private Integer equipmentCount;
    /** 默认班组 ID（base_team.id） */
    private Long teamId;
    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private String status;
    /** 备注 */
    private String remark;
}
