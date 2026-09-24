package com.mes.material.dto.vo;

import com.mes.material.entity.MatShopStock;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 线边仓库存 VO。
 */
@Data
public class ShopStockVO {
    private Long id;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String batchNo;
    private Long workcenterId;
    private String workcenterName;
    private BigDecimal qty;

    /**
     * 实体转 VO。
     *
     * @param e 库存实体
     * @return VO
     */
    public static ShopStockVO from(MatShopStock e) {
        ShopStockVO vo = new ShopStockVO();
        vo.setId(e.getId());
        vo.setMaterialId(e.getMaterialId());
        vo.setBatchNo(e.getBatchNo());
        vo.setWorkcenterId(e.getWorkcenterId());
        vo.setQty(e.getQty());
        return vo;
    }
}
