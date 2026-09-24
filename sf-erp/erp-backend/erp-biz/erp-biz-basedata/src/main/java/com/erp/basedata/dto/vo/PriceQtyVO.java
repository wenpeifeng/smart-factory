package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.SalPriceQty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售批量价 VO（冗余物料信息由 Service 填充）。
 */
@Data
public class PriceQtyVO {

    /** 主键 */
    private Long id;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码（冗余） */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 起订量（含） */
    private BigDecimal minQty;

    /** 批量单价（不含税） */
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
    public static PriceQtyVO from(SalPriceQty entity) {
        PriceQtyVO vo = new PriceQtyVO();
        vo.setId(entity.getId());
        vo.setMaterialId(entity.getMaterialId());
        vo.setMinQty(entity.getMinQty());
        vo.setPrice(entity.getPrice());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
