package com.mes.execution.dto.vo;

import com.mes.execution.entity.ExeWip;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 在制品汇总 VO。
 */
@Data
public class WipVO {
    private Long id;
    private Long workorderId;
    private String woNo;
    private Long woStepId;
    private Integer seqNo;
    private String operationName;
    private Long workcenterId;
    private BigDecimal inputQty;
    private BigDecimal outputQty;
    private BigDecimal wipQty;
    private BigDecimal scrapQty;
    private BigDecimal reworkQty;
    private LocalDateTime lastStationTime;

    /**
     * 实体转 VO。
     *
     * @param e WIP 实体
     * @return VO
     */
    public static WipVO from(ExeWip e) {
        WipVO vo = new WipVO();
        vo.setId(e.getId());
        vo.setWorkorderId(e.getWorkorderId());
        vo.setWoNo(e.getWoNo());
        vo.setWoStepId(e.getWoStepId());
        vo.setSeqNo(e.getSeqNo());
        vo.setOperationName(e.getOperationName());
        vo.setWorkcenterId(e.getWorkcenterId());
        vo.setInputQty(e.getInputQty());
        vo.setOutputQty(e.getOutputQty());
        vo.setWipQty(e.getWipQty());
        vo.setScrapQty(e.getScrapQty());
        vo.setReworkQty(e.getReworkQty());
        vo.setLastStationTime(e.getLastStationTime());
        return vo;
    }
}
