package com.mes.quality.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 检验单实体（表 qc_inspect）。
 * <p>状态机：0 草稿（录入中）→ 1 已提交（汇总结论生成，回传 ERP）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("qc_inspect")
public class QcInspect extends BaseEntity {
    /** 检验单号（JC+yyyyMMdd+4位流水） */
    private String inspectNo;
    /** 检验类型：IQC / IPQC / FQC */
    private String inspectType;
    /** 来源类型：PURCHASE 到货 / WORKORDER 工序 / FINISH 完工 */
    private String sourceType;
    /** 来源单据 ID */
    private Long sourceId;
    /** 来源单号 */
    private String sourceNo;
    /** 检验模板 ID */
    private Long templateId;
    /** 工单 ID */
    private Long workorderId;
    /** 工单工序 ID */
    private Long woStepId;
    /** 物料 ID */
    private Long materialId;
    /** 供应商 ID（IQC） */
    private Long supplierId;
    /** 批次号 */
    private String batchNo;
    /** 抽检数量 */
    private BigDecimal sampleQty;
    /** 合格数量 */
    private BigDecimal qualifiedQty;
    /** 不合格数量 */
    private BigDecimal unqualifiedQty;
    /** 检验结论：1 合格 / 0 不合格 */
    private Integer conclusion;
    /** 检验员姓名 */
    private String inspectorName;
    /** 检验日期 */
    private LocalDate inspectDate;
    /** 状态：0 草稿 / 1 已提交 */
    private Integer status;
    /** 提交时间 */
    private LocalDateTime submitTime;
    /** 备注 */
    private String remark;
}
