package com.erp.qc.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 不合格品处置单 VO（列表行 + 详情）。
 */
@Data
public class QcRejectVO {

    private Long id;

    /** 处置单号 */
    private String rejectNo;

    /** 来源检验单 ID */
    private Long inspectId;

    /** 来源检验单号 */
    private String inspectNo;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 物料规格 */
    private String spec;

    /** 计量单位 */
    private String unit;

    /** 处置数量 */
    private BigDecimal qty;

    /** 处置决定：1退货 2返工 3让步接收 4报废 */
    private Integer decision;

    /** 处置说明 */
    private String disposeRemark;

    /** 审批人 */
    private String approver;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 创建日期（筛选辅助） */
    private LocalDate createDate;
}
