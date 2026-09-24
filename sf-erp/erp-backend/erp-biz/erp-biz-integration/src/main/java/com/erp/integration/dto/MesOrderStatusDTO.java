package com.erp.integration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MES 工单进度镜像回传 DTO（MES -> ERP 入站契约，六类接口之 #3）。
 * <p>MES 工单状态机变更（开工/完工/关闭/红冲）时回传，ERP 只更新
 * mrp_work_order.mes_status 执行状态镜像列与数量快照，不回写 ERP 状态机
 * （双向同步不回写对方源数据的业务状态字段，只做状态镜像 —— 契约第 1 节原则）。
 */
@Data
public class MesOrderStatusDTO {
    /** MES 工单号 */
    @NotBlank(message = "工单号不能为空")
    private String woNo;
    /** 来源 ERP 生产订单号（ERP 侧关联键） */
    private String erpOrderNo;
    /** MES 工单状态镜像（STARTED/FINISHED/CLOSED 等，MES 侧状态字典原样透传） */
    @NotBlank(message = "MES工单状态不能为空")
    private String mesStatus;
    /** MES 累计合格数量（快照，空=不上报） */
    private BigDecimal goodQty;
    /** MES 累计报废数量（快照，空=不上报） */
    private BigDecimal scrapQty;
    /** 事件时间 */
    private LocalDateTime eventTime;
}
