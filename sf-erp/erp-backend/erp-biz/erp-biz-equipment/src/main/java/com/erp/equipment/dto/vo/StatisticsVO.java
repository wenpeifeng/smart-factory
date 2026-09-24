package com.erp.equipment.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 设备管理统计 VO（GET /equipment/statistics）。
 * <p>
 * 口径：月度维修次数与备件消耗成本均按「当月」（自然月，东八区服务器时间）。
 */
@Data
public class StatisticsVO {

    /** 设备状态分布（在用台账按 status 计数，四种状态全量返回，无设备的状态计 0） */
    private List<StatusCount> statusDist;

    /** 当月维修次数 TOP5 设备（按工单报修时间归月，不足 5 条按实际返回） */
    private List<DeviceRepairTop> monthRepairTop;

    /** 当月备件消耗成本（完工工单的备件明细 cost_ref 合计） */
    private BigDecimal monthSpareCost;

    /** 状态分布项 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusCount {

        /** 状态码：1运行 2停机 3维修中 4报废 */
        private Integer status;

        /** 状态中文 */
        private String statusLabel;

        /** 台账数量 */
        private Long count;
    }

    /** 当月维修次数 TOP 设备项 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceRepairTop {

        /** 设备 ID */
        private Long deviceId;

        /** 设备编号 */
        private String deviceCode;

        /** 设备名称 */
        private String deviceName;

        /** 当月维修次数 */
        private Long repairCount;
    }
}
