package com.mes.quality.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 不合格品处置单实体（表 qc_reject）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("qc_reject")
public class QcReject extends BaseEntity {
    /** 处置单号（BH+yyyyMMdd+4位流水） */
    private String rejectNo;
    /** 检验单 ID */
    private Long inspectId;
    /** 检验单号（快照） */
    private String inspectNo;
    /** 物料 ID */
    private Long materialId;
    /** 处置数量（累计不超过检验单不合格数） */
    private BigDecimal qty;
    /** 处置决定：1 返工 / 2 让步接收 / 3 报废 / 4 退货 */
    private Integer decision;
    /** 处置说明 */
    private String disposeRemark;
    /** 审批人 */
    private String approver;
}
