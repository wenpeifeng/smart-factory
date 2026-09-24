package com.erp.cost.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 计价流水行 VO（分页出参，MyBatis XML 直接映射）。
 */
@Data
public class CostFlowVO {

    private Long id;

    /** 业务类型编码 */
    private String bizType;

    /** 业务类型中文说明 */
    private String bizTypeLabel;

    /** 来源单据 ID */
    private Long bizId;

    /** 来源单号 */
    private String bizNo;

    /** 关联库存流水 ID（sync 重算回填） */
    private Long movementId;

    private Long materialId;
    private String materialCode;
    private String materialName;

    private Long warehouseId;
    private String warehouseName;

    /** 业务日期 */
    private LocalDate bizDate;

    /** 数量（正入负出） */
    private BigDecimal qty;

    /** 单价（入库行=本次入库单价；出库行=当时移动加权价） */
    private BigDecimal unitCost;

    /** 金额（与数量同号） */
    private BigDecimal amount;

    /** 运行余额：结存数量 */
    private BigDecimal balanceQty;

    /** 运行余额：结存金额 */
    private BigDecimal balanceCost;

    /** 期间 yyyyMM */
    private String period;

    /** 业务类型中文说明（由服务层从枚举补充，未识别返回原编码） */
    public static String labelOf(String bizType) {
        var t = com.erp.cost.constant.CostBizType.ofCode(bizType);
        return t == null ? bizType : t.getLabel();
    }
}
