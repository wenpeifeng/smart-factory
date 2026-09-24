package com.mes.trace.dto.vo;

import com.mes.trace.entity.TraceLink;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 追溯链节点 VO（正反向共用，direction 由查询语义决定）。
 */
@Data
public class TraceNodeVO {
    private Long id;
    /** 查询起点的对端编号：正向=下游产品，反向=来料批次/关键件SN */
    private String targetNo;
    /** 对端类型：BATCH / SN / WO（工单产出） */
    private String targetType;
    private Long workorderId;
    private String woNo;
    private Long woStepId;
    private Long materialId;
    private BigDecimal qty;
    private LocalDateTime linkTime;

    /**
     * 实体转 VO。
     *
     * @param e 追溯关系实体
     * @param targetNo 对端编号
     * @return VO
     */
    public static TraceNodeVO from(TraceLink e, String targetNo, String targetType) {
        TraceNodeVO vo = new TraceNodeVO();
        vo.setId(e.getId());
        vo.setTargetNo(targetNo);
        vo.setTargetType(targetType);
        vo.setWorkorderId(e.getWorkorderId());
        vo.setWoStepId(e.getWoStepId());
        vo.setMaterialId(e.getMaterialId());
        vo.setQty(e.getQty());
        vo.setLinkTime(e.getLinkTime());
        return vo;
    }
}
