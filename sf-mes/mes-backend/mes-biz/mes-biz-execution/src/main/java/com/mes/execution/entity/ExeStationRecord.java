package com.mes.execution.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 过站扫码记录实体（表 exe_station_record，一次扫码/一笔报工）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("exe_station_record")
public class ExeStationRecord extends BaseEntity {
    /** 记录号（GZ+yyyyMMdd+4位流水） */
    private String recordNo;
    /** 工单 ID（plan_workorder.id） */
    private Long workorderId;
    /** 工单号（快照） */
    private String woNo;
    /** 工单工序 ID（plan_wo_step.id） */
    private Long woStepId;
    /** 工序序号（快照） */
    private Integer seqNo;
    /** 工序 ID */
    private Long operationId;
    /** 工序名称（快照） */
    private String operationName;
    /** 工作中心 ID */
    private Long workcenterId;
    /** 设备 ID（eqp_device.id） */
    private Long deviceId;
    /** 产品 SN（SN 模式工单） */
    private String sn;
    /** 关键件 SN（逗号分隔） */
    private String keyComponentSn;
    /** 合格数量 */
    private BigDecimal goodQty;
    /** 报废数量 */
    private BigDecimal scrapQty;
    /** 返修数量 */
    private BigDecimal reworkQty;
    /** 工时（分钟） */
    private Integer workMinutes;
    /** 报工人 ID（sys_user.id） */
    private Long workerId;
    /** 报工人姓名 */
    private String workerName;
    /** 班组 ID（base_team.id） */
    private Long teamId;
    /** 过站时间 */
    private LocalDateTime stationTime;
    /** 过站结果：PASS / FAIL */
    private String result;
}
