package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * BOM 子件明细 VO（含物料编码/名称回显）。
 */
@Data
public class BomItemVO implements Serializable {

    /** 明细ID */
    private Long id;

    /** 子件物料ID */
    private Long materialId;

    /** 子件物料编码 */
    private String materialCode;

    /** 子件物料名称 */
    private String materialName;

    /** 规格型号 */
    private String spec;

    /** 计量单位 */
    private String unit;

    /** 单位用量 */
    private BigDecimal qty;

    /** 损耗率% */
    private BigDecimal lossRate;

    /** 备注 */
    private String remark;

    /** 由实体+物料信息组装 VO */
    public static BomItemVO of(Long id, Long materialId, BigDecimal qty, BigDecimal lossRate, String remark,
                               MaterialBriefVO material) {
        BomItemVO vo = new BomItemVO();
        vo.setId(id);
        vo.setMaterialId(materialId);
        vo.setQty(qty);
        vo.setLossRate(lossRate);
        vo.setRemark(remark);
        if (material != null) {
            vo.setMaterialCode(material.getCode());
            vo.setMaterialName(material.getName());
            vo.setSpec(material.getSpec());
            vo.setUnit(material.getUnit());
        }
        return vo;
    }
}
