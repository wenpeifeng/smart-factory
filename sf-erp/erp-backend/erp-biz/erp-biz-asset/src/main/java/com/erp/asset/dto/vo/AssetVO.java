package com.erp.asset.dto.vo;

import com.erp.asset.entity.AstAsset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产卡片 VO（含类别/部门/供应商名称回显；净值由数据库生成列带出）。
 */
public record AssetVO(
        Long id,
        String assetCode,
        String name,
        Long categoryId,
        String categoryName,
        String spec,
        Long supplierId,
        String supplierName,
        String location,
        Long deptId,
        String deptName,
        String keeper,
        BigDecimal originalValue,
        BigDecimal residualValue,
        Integer expectedLifeMonths,
        Integer depreciatedMonths,
        BigDecimal accumulatedDepreciation,
        BigDecimal netValue,
        LocalDate accountDate,
        String status,
        Long entryVoucherId,
        String disposeMethod,
        BigDecimal disposeIncome,
        LocalDateTime disposeTime,
        Long disposeVoucherId,
        String remark,
        LocalDateTime createTime) {

    /** 实体 → VO（名称由 Service 批量解析后传入，允许 null） */
    public static AssetVO from(AstAsset a, String categoryName, String deptName, String supplierName) {
        return new AssetVO(a.getId(), a.getAssetCode(), a.getName(), a.getCategoryId(), categoryName,
                a.getSpec(), a.getSupplierId(), supplierName, a.getLocation(), a.getDeptId(), deptName,
                a.getKeeper(), a.getOriginalValue(), a.getResidualValue(), a.getExpectedLifeMonths(),
                a.getDepreciatedMonths(), a.getAccumulatedDepreciation(), a.getNetValue(),
                a.getAccountDate(), a.getStatus(), a.getEntryVoucherId(), a.getDisposeMethod(),
                a.getDisposeIncome(), a.getDisposeTime(), a.getDisposeVoucherId(),
                a.getRemark(), a.getCreateTime());
    }
}
