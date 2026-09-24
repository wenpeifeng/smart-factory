package com.erp.mrp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产订单 —— 状态机单向流转：
 * DRAFT草稿 → RELEASED已下达 → PRODUCING生产中(首次领料审核后) → FINISHED已完工 → CLOSED已关闭。
 * <p>
 * 约束：下达时校验 BOM 仍为 ACTIVE；完工按“良品+不良”累计，达到订单数量自动 FINISHED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mrp_work_order")
public class MrpWorkOrder extends BaseEntity implements Serializable {

    /** 单号（MO+yyyyMMdd+4位流水） */
    private String orderNo;

    /** 成品物料ID */
    private Long materialId;

    /** 计划生产数量 */
    private BigDecimal qty;

    /** BOMID（下达时校验仍有效） */
    private Long bomId;

    /** BOM版本号快照 */
    private String bomVersion;

    /** 默认入库仓库 */
    private Long warehouseId;

    /** 计划开始日期 */
    private LocalDate planStartDate;

    /** 计划结束日期 */
    private LocalDate planEndDate;

    /** 领料方式：DIRECT直接领料 / BACKFLUSH倒冲（完工报工按BOM自动扣料，V621） */
    private String pickType;

    /** 倒冲扣料仓库（空=沿用 warehouse_id，V621） */
    private Long backflushWarehouseId;

    /** 状态：DRAFT RELEASED PRODUCING FINISHED CLOSED */
    private String status;

    /**
     * MES 执行状态镜像（V1231 ERP-MES 集成）：MES 工单状态机原样透传
     * （STARTED/FINISHED/CLOSED 等），仅展示与追溯用，不参与 ERP 状态机流转。
     */
    private String mesStatus;

    /** 累计完工良品数量 */
    private BigDecimal finishQty;

    /** 累计不良品数量 */
    private BigDecimal badQty;

    /**
     * 审批流发起标记（三段式下达依据，V2101）：
     * 0=从未发起过流程；1=已发起过（再次点下达按 WorkflowService.isApproved 判定，
     * 审批通过即执行下达；驳回后重新点下达=重新发起新实例）。
     */
    private Integer wfStarted;

    /** 来源：MANUAL手工 MRP_SUGGESTION建议转单 */
    private String sourceType;

    /** 来源单据ID */
    private Long sourceId;

    /** 备注 */
    private String remark;
}
