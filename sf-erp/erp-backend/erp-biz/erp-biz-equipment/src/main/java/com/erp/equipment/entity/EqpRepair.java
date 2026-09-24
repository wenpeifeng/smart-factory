package com.erp.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备维修工单实体（头）。
 * <p>
 * 工单状态机（单向，EqpRepairServiceImpl 校验）：
 * 0 待维修 --接单--> 1 维修中 --完工--> 2 已修复 --关闭--> 3 已关闭。
 * 完工时按明细领用备件（扣 inv_stock + 写 EQP_OUT 流水），并联动设备状态回「运行」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eqp_repair")
public class EqpRepair extends BaseEntity {

    /** 工单状态：待维修 */
    public static final int STATUS_PENDING = 0;
    /** 工单状态：维修中 */
    public static final int STATUS_REPAIRING = 1;
    /** 工单状态：已修复 */
    public static final int STATUS_FIXED = 2;
    /** 工单状态：已关闭 */
    public static final int STATUS_CLOSED = 3;

    /** 工单号（WX+yyyyMMdd+4位流水，Redis 自增生成） */
    private String repairNo;

    /** 设备 ID（eqp_device.id） */
    private Long deviceId;

    /** 故障描述 */
    private String faultDesc;

    /** 报修人（文本） */
    private String reporter;

    /** 报修时间 */
    private LocalDateTime reportTime;

    /** 工单状态：0待维修 1维修中 2已修复 3已关闭 */
    private Integer status;

    /** 维修人（接单时填写） */
    private String repairer;

    /** 故障原因（完工时填写） */
    private String faultReason;

    /** 备件出库仓库（base_warehouse.id，完工领料用） */
    private Long warehouseId;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 备注 */
    private String remark;
}
