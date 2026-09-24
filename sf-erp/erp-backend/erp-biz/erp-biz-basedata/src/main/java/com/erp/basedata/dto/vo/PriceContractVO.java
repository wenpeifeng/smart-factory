package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.BasePriceContract;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购合同价 VO（冗余供应商/物料名称由 Service 填充）。
 */
@Data
public class PriceContractVO {

    /** 主键 */
    private Long id;

    /** 供应商 ID */
    private Long supplierId;

    /** 供应商名称（冗余） */
    private String supplierName;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码（冗余） */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 合同单价（不含税） */
    private BigDecimal contractPrice;

    /** 生效起（含） */
    private LocalDate effectiveFrom;

    /** 生效止（含） */
    private LocalDate effectiveTo;

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
    public static PriceContractVO from(BasePriceContract entity) {
        PriceContractVO vo = new PriceContractVO();
        vo.setId(entity.getId());
        vo.setSupplierId(entity.getSupplierId());
        vo.setMaterialId(entity.getMaterialId());
        vo.setContractPrice(entity.getContractPrice());
        vo.setEffectiveFrom(entity.getEffectiveFrom());
        vo.setEffectiveTo(entity.getEffectiveTo());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
