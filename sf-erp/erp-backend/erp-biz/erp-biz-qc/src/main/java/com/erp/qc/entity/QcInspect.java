package com.erp.qc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 检验单主表实体（qc_inspect）。
 * <p>
 * 状态机：{@link #STATUS_DRAFT 草稿} → {@link #STATUS_INSPECTED 已检验}（单向，条件 UPDATE 防并发）。
 * 来源单据（source_type + source_id + source_no 快照）挂接一期模块：
 * pur_instock → IQC、mrp_work_order → FQC、sal_return → IQC（复检）。
 * 提交检验时回填 合格数/不合格数/结论/检验人/检验日期，口径：合格数+不合格数=抽检数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_inspect")
public class QcInspect extends BaseEntity {

    /** 状态：草稿（待检验） */
    public static final int STATUS_DRAFT = 0;
    /** 状态：已检验（终态） */
    public static final int STATUS_INSPECTED = 10;

    /** 检验结论：合格 */
    public static final int CONCLUSION_PASS = 1;
    /** 检验结论：让步接收 */
    public static final int CONCLUSION_CONCESSION = 2;
    /** 检验结论：退货 */
    public static final int CONCLUSION_REJECT = 3;

    /** 来源单据类型：采购入库单 */
    public static final String SOURCE_PUR_INSTOCK = "pur_instock";
    /** 来源单据类型：生产工单 */
    public static final String SOURCE_MRP_WORK_ORDER = "mrp_work_order";
    /** 来源单据类型：销售退货单 */
    public static final String SOURCE_SAL_RETURN = "sal_return";

    /** 检验单号（QC+yyyyMMdd+4位流水，全局唯一） */
    private String inspectNo;

    /** 检验类型：IQC/IPQC/FQC/OQC */
    private String inspectType;

    /** 来源单据类型：pur_instock/mrp_work_order/sal_return，空=手工创建 */
    private String sourceType;

    /** 来源单据 ID */
    private Long sourceId;

    /** 来源单号（快照） */
    private String sourceNo;

    /** 套用的检验模板 ID（明细为其快照） */
    private Long templateId;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 供应商 ID（base_supplier.id，IQC 来源采购入库时填写） */
    private Long supplierId;

    /** 抽检数量 */
    private BigDecimal sampleQty;

    /** 合格数量（提交检验回填） */
    private BigDecimal qualifiedQty;

    /** 不合格数量（提交检验回填） */
    private BigDecimal unqualifiedQty;

    /** 检验结论：1合格 2让步接收 3退货（草稿为空） */
    private Integer conclusion;

    /** 检验人姓名 */
    private String inspectorName;

    /** 检验日期 */
    private LocalDate inspectDate;

    /** 状态：0草稿 10已检验 */
    private Integer status;

    /** 提交检验时间 */
    private LocalDateTime submitTime;

    /** 备注 */
    private String remark;

    /** 创建时间（MP 填充，列表展示用） */
    private LocalDateTime createTime;
}
