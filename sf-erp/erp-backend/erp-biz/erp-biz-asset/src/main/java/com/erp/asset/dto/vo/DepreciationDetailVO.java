package com.erp.asset.dto.vo;

import java.math.BigDecimal;

/**
 * 折旧计提明细行 VO（期间明细表）。
 *
 * @param id           明细 ID
 * @param assetId      资产 ID
 * @param assetCode    资产编码
 * @param assetName    资产名称
 * @param categoryName 资产类别名称
 * @param deptName     使用部门名称
 * @param monthAmount  本月折旧额
 * @param voucherId    折旧凭证 ID
 */
public record DepreciationDetailVO(
        Long id,
        Long assetId,
        String assetCode,
        String assetName,
        String categoryName,
        String deptName,
        BigDecimal monthAmount,
        Long voucherId) {
}
