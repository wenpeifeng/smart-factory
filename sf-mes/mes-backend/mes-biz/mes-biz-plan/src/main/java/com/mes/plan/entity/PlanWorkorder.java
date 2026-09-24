package com.mes.plan.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产工单实体（表 plan_workorder）。
 * <p>状态机：DRAFT → RELEASED → PRODUCING → FINISHED → CLOSED，
 * 流转校验集中在 {@code WorkorderServiceImpl}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("plan_workorder")
public class PlanWorkorder extends BaseEntity {
    /** 工单号（MO+yyyyMMdd+4位流水） */
    private String woNo;
    /** 成品物料 ID（base_material.id） */
    private Long materialId;
    /** 计划生产数量 */
    private BigDecimal qty;
    /** BOM ID */
    private Long bomId;
    /** BOM 版本号快照 */
    private String bomVersion;
    /** 工艺路线 ID（空=无工序单工序报工） */
    private Long routingId;
    /** 完工入库仓库 ID（base_warehouse.id，回传 ERP 用） */
    private Long warehouseId;
    /** 计划开始日期 */
    private LocalDate planStartDate;
    /** 计划结束日期 */
    private LocalDate planEndDate;
    /** 状态：DRAFT/RELEASED/PRODUCING/FINISHED/CLOSED */
    private String status;
    /** 累计完工良品数量 */
    private BigDecimal finishQty;
    /** 累计报废数量 */
    private BigDecimal scrapQty;
    /** 追踪模式：BATCH 批次 / SN 单品序列号 */
    private String snMode;
    /** 来源：ERP_ORDER ERP 生产订单下推 / MANUAL 手工 */
    private String sourceType;
    /** 来源单号（ERP 生产订单号） */
    private String sourceNo;
    /** 来源单据 ID（ERP 侧） */
    private Long sourceId;
    /** 结案说明 */
    private String closeRemark;
    /** 备注 */
    private String remark;
}
