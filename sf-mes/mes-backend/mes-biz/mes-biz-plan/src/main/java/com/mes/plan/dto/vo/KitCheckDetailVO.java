package com.mes.plan.dto.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 齐套分析详情 VO（含逐料结果行）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KitCheckDetailVO extends KitCheckVO {
    /** 逐料结果行 */
    private List<ItemVO> items;

    /** 齐套分析行 VO */
    @Data
    public static class ItemVO {
        private Long id;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private java.math.BigDecimal reqQty;
        private java.math.BigDecimal availableQty;
        private java.math.BigDecimal shortQty;
        private String result;
    }
}
