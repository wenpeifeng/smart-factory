package com.mes.execution.dto.vo;

import com.mes.execution.entity.ExeReworkScrap;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 返工/报废单 VO。
 */
@Data
public class ReworkScrapVO {
    private Long id;
    private String orderNo;
    private String type;
    private Long workorderId;
    private String woNo;
    private Long woStepId;
    private String operationName;
    private String sn;
    private BigDecimal qty;
    private String reason;
    private Long qcRejectId;
    private String status;
    private String handleRemark;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param e 返工/报废单实体
     * @return VO
     */
    public static ReworkScrapVO from(ExeReworkScrap e) {
        ReworkScrapVO vo = new ReworkScrapVO();
        vo.setId(e.getId());
        vo.setOrderNo(e.getOrderNo());
        vo.setType(e.getType());
        vo.setWorkorderId(e.getWorkorderId());
        vo.setWoNo(e.getWoNo());
        vo.setWoStepId(e.getWoStepId());
        vo.setOperationName(e.getOperationName());
        vo.setSn(e.getSn());
        vo.setQty(e.getQty());
        vo.setReason(e.getReason());
        vo.setQcRejectId(e.getQcRejectId());
        vo.setStatus(e.getStatus());
        vo.setHandleRemark(e.getHandleRemark());
        vo.setHandleTime(e.getHandleTime());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
