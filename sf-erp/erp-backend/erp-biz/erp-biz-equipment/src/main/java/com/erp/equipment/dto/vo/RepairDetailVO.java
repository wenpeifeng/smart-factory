package com.erp.equipment.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 维修工单详情 VO（头 + 备件明细行）。
 */
@Data
public class RepairDetailVO {

    /** 工单 ID */
    private Long id;

    /** 工单号 */
    private String repairNo;

    /** 设备 ID */
    private Long deviceId;

    /** 设备编号 */
    private String deviceCode;

    /** 设备名称 */
    private String deviceName;

    /** 故障描述 */
    private String faultDesc;

    /** 报修人 */
    private String reporter;

    /** 报修时间 */
    private LocalDateTime reportTime;

    /** 工单状态：0待维修 1维修中 2已修复 3已关闭 */
    private Integer status;

    /** 维修人 */
    private String repairer;

    /** 故障原因 */
    private String faultReason;

    /** 备件出库仓库 ID */
    private Long warehouseId;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 备注 */
    private String remark;

    /** 备件明细行 */
    private List<ItemVO> lines;

    /** 备件明细行 VO */
    @Data
    public static class ItemVO {

        /** 明细 ID */
        private Long id;

        /** 物料 ID */
        private Long materialId;

        /** 物料编码（JOIN base_material） */
        private String materialCode;

        /** 物料名称（JOIN base_material） */
        private String materialName;

        /** 使用数量 */
        private BigDecimal qty;

        /** 成本参考（数量 × 物料参考成本快照） */
        private BigDecimal costRef;
    }
}
