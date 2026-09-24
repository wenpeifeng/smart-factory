package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.SalPriceLevel;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售等级价 VO（冗余物料信息由 Service 填充）。
 */
@Data
public class PriceLevelVO {

    /** 主键 */
    private Long id;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码（冗余） */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 客户等级：A/B/C */
    private String level;

    /** 等级单价（不含税） */
    private BigDecimal price;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /**
     * 从实体转换。
     *
     * @param entity 实体
     * @return VO
     */
    public static PriceLevelVO from(SalPriceLevel entity) {
        PriceLevelVO vo = new PriceLevelVO();
        vo.setId(entity.getId());
        vo.setMaterialId(entity.getMaterialId());
        vo.setLevel(entity.getLevel());
        vo.setPrice(entity.getPrice());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
