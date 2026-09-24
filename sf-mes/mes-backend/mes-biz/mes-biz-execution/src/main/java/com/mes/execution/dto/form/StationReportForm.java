package com.mes.execution.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 过站扫码报工表单（车间终端/移动端提交）。
 */
@Data
public class StationReportForm {
    /** 工单工序 ID（plan_wo_step.id，扫码定位） */
    @NotNull(message = "工序任务不能为空")
    private Long woStepId;
    /** 产品 SN（SN 模式工单） */
    private String sn;
    /** 关键件 SN（逗号分隔，关键件采集点必填） */
    private String keyComponentSn;
    /** 合格数量 */
    @NotNull(message = "合格数量不能为空")
    @PositiveOrZero(message = "合格数量不能为负")
    private BigDecimal goodQty;
    /** 报废数量 */
    @NotNull(message = "报废数量不能为空")
    @PositiveOrZero(message = "报废数量不能为负")
    private BigDecimal scrapQty;
    /** 返修数量 */
    @PositiveOrZero(message = "返修数量不能为负")
    private BigDecimal reworkQty;
    /** 工时（分钟） */
    @PositiveOrZero(message = "工时不能为负")
    private Integer workMinutes;
    /** 设备 ID（eqp_device.id） */
    private Long deviceId;
    /** 班组 ID（base_team.id） */
    private Long teamId;
    /** 报工人姓名（快照，报工人 id 取当前登录人） */
    private String workerName;
    /** 过站结果：PASS / FAIL */
    private String result;
}
