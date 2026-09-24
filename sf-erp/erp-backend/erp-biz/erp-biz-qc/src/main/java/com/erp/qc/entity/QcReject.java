package com.erp.qc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不合格品处置单实体（qc_reject）。
 * <p>
 * 从已检验（status=10）且不合格数>0 的检验单生成；同一检验单累计处置数量 ≤ 不合格数量（服务层校验）。
 * 处置决定：1退货 2返工 3让步接收 4报废。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_reject")
public class QcReject extends BaseEntity {

    /** 处置决定：退货 */
    public static final int DECISION_RETURN = 1;
    /** 处置决定：返工 */
    public static final int DECISION_REWORK = 2;
    /** 处置决定：让步接收 */
    public static final int DECISION_CONCESSION = 3;
    /** 处置决定：报废 */
    public static final int DECISION_SCRAP = 4;

    /** 处置单号（RJ+yyyyMMdd+4位流水，全局唯一） */
    private String rejectNo;

    /** 来源检验单 ID（qc_inspect.id） */
    private Long inspectId;

    /** 来源检验单号（快照） */
    private String inspectNo;

    /** 物料 ID（base_material.id） */
    private Long materialId;

    /** 处置数量（>0） */
    private BigDecimal qty;

    /** 处置决定：1退货 2返工 3让步接收 4报废 */
    private Integer decision;

    /** 处置说明 */
    private String disposeRemark;

    /** 审批人 */
    private String approver;

    /** 创建时间（MP 填充，列表展示用） */
    private LocalDateTime createTime;
}
