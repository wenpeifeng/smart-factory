package com.mes.plan.dto.vo;

import com.mes.plan.entity.PlanWorkorder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产工单列表 VO。
 */
@Data
public class WorkorderVO {
    private Long id;
    private String woNo;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private BigDecimal qty;
    private Long bomId;
    private String bomVersion;
    private Long routingId;
    private Long warehouseId;
    private LocalDate planStartDate;
    private LocalDate planEndDate;
    private String status;
    private BigDecimal finishQty;
    private BigDecimal scrapQty;
    private String snMode;
    private String sourceType;
    private String sourceNo;
    private String remark;
    private LocalDateTime createTime;

    /**
     * 实体转 VO（物料编码名称由调用方补充）。
     *
     * @param e 工单实体
     * @return VO
     */
    public static WorkorderVO from(PlanWorkorder e) {
        WorkorderVO vo = new WorkorderVO();
        vo.setId(e.getId());
        vo.setWoNo(e.getWoNo());
        vo.setMaterialId(e.getMaterialId());
        vo.setQty(e.getQty());
        vo.setBomId(e.getBomId());
        vo.setBomVersion(e.getBomVersion());
        vo.setRoutingId(e.getRoutingId());
        vo.setWarehouseId(e.getWarehouseId());
        vo.setPlanStartDate(e.getPlanStartDate());
        vo.setPlanEndDate(e.getPlanEndDate());
        vo.setStatus(e.getStatus());
        vo.setFinishQty(e.getFinishQty());
        vo.setScrapQty(e.getScrapQty());
        vo.setSnMode(e.getSnMode());
        vo.setSourceType(e.getSourceType());
        vo.setSourceNo(e.getSourceNo());
        vo.setRemark(e.getRemark());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
