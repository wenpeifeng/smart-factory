package com.mes.quality.dto.vo;

import com.mes.quality.entity.QcSpcRule;
import lombok.Data;

import java.math.BigDecimal;

/**
 * SPC 规则 VO。
 */
@Data
public class QcSpcRuleVO {
    private Long id;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String itemName;
    private BigDecimal ucl;
    private BigDecimal lcl;
    private BigDecimal target;
    private Integer ruleR1;
    private Integer ruleR2;
    private Integer ruleR3;
    private Integer ruleR4;
    private Integer ruleR5;
    private Integer status;
    private String remark;

    /**
     * 实体转 VO。
     *
     * @param e 规则实体
     * @return VO
     */
    public static QcSpcRuleVO from(QcSpcRule e) {
        QcSpcRuleVO vo = new QcSpcRuleVO();
        vo.setId(e.getId());
        vo.setMaterialId(e.getMaterialId());
        vo.setItemName(e.getItemName());
        vo.setUcl(e.getUcl());
        vo.setLcl(e.getLcl());
        vo.setTarget(e.getTarget());
        vo.setRuleR1(e.getRuleR1());
        vo.setRuleR2(e.getRuleR2());
        vo.setRuleR3(e.getRuleR3());
        vo.setRuleR4(e.getRuleR4());
        vo.setRuleR5(e.getRuleR5());
        vo.setStatus(e.getStatus());
        vo.setRemark(e.getRemark());
        return vo;
    }
}
