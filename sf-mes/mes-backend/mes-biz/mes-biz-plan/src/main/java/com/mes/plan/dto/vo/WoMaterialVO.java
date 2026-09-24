package com.mes.plan.dto.vo;

import com.mes.plan.entity.PlanWoMaterial;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工单用料需求 VO。
 */
@Data
public class WoMaterialVO {
    private Long id;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private BigDecimal reqQty;
    private BigDecimal issuedQty;

    /**
     * 实体转 VO（物料编码名称由调用方补充）。
     *
     * @param e 用料需求实体
     * @return VO
     */
    public static WoMaterialVO from(PlanWoMaterial e) {
        WoMaterialVO vo = new WoMaterialVO();
        vo.setId(e.getId());
        vo.setMaterialId(e.getMaterialId());
        vo.setReqQty(e.getReqQty());
        vo.setIssuedQty(e.getIssuedQty());
        return vo;
    }
}
