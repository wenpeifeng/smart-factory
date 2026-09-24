package com.mes.plan.dto.vo;

import com.mes.plan.entity.PlanWoStep;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单工序任务 VO。
 */
@Data
public class WoStepVO {
    private Long id;
    private Long workorderId;
    private Integer seqNo;
    private Long operationId;
    private String operationName;
    private Long workcenterId;
    private String workcenterName;
    private BigDecimal unitMinutes;
    private Integer setupMinutes;
    private BigDecimal planQty;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private String status;
    private Long assigneeId;
    private String assigneeName;
    private LocalDateTime dispatchTime;
    private BigDecimal finishQty;
    private BigDecimal scrapQty;
    private BigDecimal reworkQty;
    private Integer qcFlag;

    /**
     * 实体转 VO（工作中心名称由调用方补充）。
     *
     * @param e 工序任务实体
     * @return VO
     */
    public static WoStepVO from(PlanWoStep e) {
        WoStepVO vo = new WoStepVO();
        vo.setId(e.getId());
        vo.setWorkorderId(e.getWorkorderId());
        vo.setSeqNo(e.getSeqNo());
        vo.setOperationId(e.getOperationId());
        vo.setOperationName(e.getOperationName());
        vo.setWorkcenterId(e.getWorkcenterId());
        vo.setUnitMinutes(e.getUnitMinutes());
        vo.setSetupMinutes(e.getSetupMinutes());
        vo.setPlanQty(e.getPlanQty());
        vo.setPlanStartTime(e.getPlanStartTime());
        vo.setPlanEndTime(e.getPlanEndTime());
        vo.setStatus(e.getStatus());
        vo.setAssigneeId(e.getAssigneeId());
        vo.setAssigneeName(e.getAssigneeName());
        vo.setDispatchTime(e.getDispatchTime());
        vo.setFinishQty(e.getFinishQty());
        vo.setScrapQty(e.getScrapQty());
        vo.setReworkQty(e.getReworkQty());
        vo.setQcFlag(e.getQcFlag());
        return vo;
    }
}
