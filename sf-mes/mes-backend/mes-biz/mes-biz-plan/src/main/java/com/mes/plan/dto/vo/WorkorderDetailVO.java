package com.mes.plan.dto.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 生产工单详情 VO（列表 VO + 工序行 + 用料行）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkorderDetailVO extends WorkorderVO {
    /** 结案说明 */
    private String closeRemark;
    /** 工序任务行 */
    private List<WoStepVO> steps;
    /** 用料需求行 */
    private List<WoMaterialVO> materials;
}
