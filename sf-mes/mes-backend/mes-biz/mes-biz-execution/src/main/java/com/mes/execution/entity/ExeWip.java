package com.mes.execution.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 在制品汇总实体（表 exe_wip，工单 x 工序维度的投入/产出累计）。
 * <p>wip_qty = input_qty - output_qty - scrap_qty，由过站报工事务内维护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("exe_wip")
public class ExeWip extends BaseEntity {
    /** 工单 ID */
    private Long workorderId;
    /** 工单号（快照） */
    private String woNo;
    /** 工单工序 ID */
    private Long woStepId;
    /** 工序序号 */
    private Integer seqNo;
    /** 工序名称（快照） */
    private String operationName;
    /** 工作中心 ID */
    private Long workcenterId;
    /** 投入数量 */
    private BigDecimal inputQty;
    /** 产出数量（合格） */
    private BigDecimal outputQty;
    /** 在制数量 */
    private BigDecimal wipQty;
    /** 报废数量 */
    private BigDecimal scrapQty;
    /** 返修数量 */
    private BigDecimal reworkQty;
    /** 最近过站时间 */
    private LocalDateTime lastStationTime;
}
