package com.erp.qc.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 可生成不合格品处置单的检验单选项（已检验且不合格数>0，扣除已处置后仍有余额）。
 */
@Data
public class RejectSourceVO {

    /** 检验单 ID */
    private Long inspectId;

    /** 检验单号 */
    private String inspectNo;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 不合格数量 */
    private BigDecimal unqualifiedQty;

    /** 已处置数量 */
    private BigDecimal disposedQty;

    /** 剩余可处置数量 */
    private BigDecimal rejectableQty;
}
