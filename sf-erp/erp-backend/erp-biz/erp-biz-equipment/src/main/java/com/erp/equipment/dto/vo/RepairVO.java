package com.erp.equipment.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 维修工单列表 VO（含设备冗余信息与备件成本合计）。
 */
@Data
public class RepairVO {

    /** 工单 ID */
    private Long id;

    /** 工单号 */
    private String repairNo;

    /** 设备 ID */
    private Long deviceId;

    /** 设备编号（JOIN eqp_device） */
    private String deviceCode;

    /** 设备名称（JOIN eqp_device） */
    private String deviceName;

    /** 故障描述 */
    private String faultDesc;

    /** 报修人 */
    private String reporter;

    /** 报修时间 */
    private LocalDateTime reportTime;

    /** 工单状态：0待维修 1维修中 2已修复 3已关闭 */
    private Integer status;

    /** 工单状态中文 */
    private String statusLabel;

    /** 维修人 */
    private String repairer;

    /** 故障原因 */
    private String faultReason;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 备件成本参考合计（子查询聚合） */
    private BigDecimal spareCost;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
