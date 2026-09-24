package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.PurPriceRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购价格流水 VO（历史价列表/详情，含供应商与物料冗余名称、来源单号可追溯）。
 */
@Data
public class PriceRecordVO {

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

    /** 成交单价（不含税） */
    private BigDecimal price;

    /** 来源类型：ORDER/INSTOCK/RFQ/CONTRACT */
    private String sourceType;

    /** 来源类型中文 */
    private String sourceTypeLabel;

    /** 来源单号（可追溯） */
    private String sourceNo;

    /** 价格日期 */
    private LocalDate priceDate;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从实体转换（冗余名称由 Service 填充）。
     *
     * @param entity 实体
     * @return VO
     */
    public static PriceRecordVO from(PurPriceRecord entity) {
        PriceRecordVO vo = new PriceRecordVO();
        vo.setId(entity.getId());
        vo.setSupplierId(entity.getSupplierId());
        vo.setMaterialId(entity.getMaterialId());
        vo.setPrice(entity.getPrice());
        vo.setSourceType(entity.getSourceType());
        vo.setSourceTypeLabel(labelOf(entity.getSourceType()));
        vo.setSourceNo(entity.getSourceNo());
        vo.setPriceDate(entity.getPriceDate());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /** 来源类型中文标签 */
    public static String labelOf(String sourceType) {
        if (sourceType == null) {
            return "";
        }
        return switch (sourceType) {
            case "ORDER" -> "采购订单";
            case "INSTOCK" -> "采购入库";
            case "RFQ" -> "询比价定标";
            case "CONTRACT" -> "合同/手工";
            default -> sourceType;
        };
    }
}
