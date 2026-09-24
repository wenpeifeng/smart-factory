package com.mes.equipment.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 设备台账实体（表 eqp_device）。
 * <p>状态：RUNNING 运行 / IDLE 闲置 / DOWN 停机 / REPAIR 维修。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("eqp_device")
public class EqpDevice extends BaseEntity {
    /** 设备编码（SB+yyyyMMdd+4位流水） */
    private String code;
    /** 设备名称 */
    private String name;
    /** 规格型号 */
    private String model;
    /** 所属工作中心 ID（base_workcenter.id） */
    private Long workcenterId;
    /** 状态：RUNNING/IDLE/DOWN/REPAIR */
    private String status;
    /** 安装位置 */
    private String location;
    /** 购置日期 */
    private LocalDate buyDate;
    /** 备注 */
    private String remark;
}
