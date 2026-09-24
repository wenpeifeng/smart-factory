package com.erp.asset.dto.vo;

import java.math.BigDecimal;

/**
 * 资产处置结果 VO。
 *
 * @param assetId    资产 ID
 * @param assetCode  资产编码
 * @param netValue   处置时净值（转入固定资产清理的金额）
 * @param income     清理收入
 * @param voucherId  处置凭证 ID
 * @param voucherNo  处置凭证号
 */
public record DisposeResultVO(
        Long assetId,
        String assetCode,
        BigDecimal netValue,
        BigDecimal income,
        Long voucherId,
        String voucherNo) {
}
