package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.PurSupplierScore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商交易评分 VO（V141）。
 */
@Data
public class SupplierScoreVO {

    private Long id;
    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private String periodType;
    private String period;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer orderCount;
    private BigDecimal orderAmount;
    private Integer instockCount;
    private BigDecimal instockQty;
    private BigDecimal ontimeRate;
    private BigDecimal qualifiedRate;
    private BigDecimal returnRate;
    private BigDecimal totalScore;
    private String grade;
    private LocalDateTime calcTime;

    public static SupplierScoreVO from(PurSupplierScore e) {
        SupplierScoreVO vo = new SupplierScoreVO();
        vo.setId(e.getId());
        vo.setSupplierId(e.getSupplierId());
        vo.setPeriodType(e.getPeriodType());
        vo.setPeriod(e.getPeriod());
        vo.setPeriodStart(e.getPeriodStart());
        vo.setPeriodEnd(e.getPeriodEnd());
        vo.setOrderCount(e.getOrderCount());
        vo.setOrderAmount(e.getOrderAmount());
        vo.setInstockCount(e.getInstockCount());
        vo.setInstockQty(e.getInstockQty());
        vo.setOntimeRate(e.getOntimeRate());
        vo.setQualifiedRate(e.getQualifiedRate());
        vo.setReturnRate(e.getReturnRate());
        vo.setTotalScore(e.getTotalScore());
        vo.setGrade(e.getGrade());
        vo.setCalcTime(e.getCalcTime());
        return vo;
    }
}
