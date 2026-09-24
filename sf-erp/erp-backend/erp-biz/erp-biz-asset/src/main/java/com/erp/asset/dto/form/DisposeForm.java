package com.erp.asset.dto.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 资产处置表单。
 *
 * @param method 处置方式：SELL-出售 SCRAP-报废 DONATE-捐赠
 * @param income 清理收入（≥0；&gt;0 时自动补 借 库存现金 / 贷 固定资产清理 分录）
 * @param remark 凭证说明（写入处置凭证备注）
 */
public record DisposeForm(
        @NotNull(message = "处置方式不能为空")
        @Pattern(regexp = "SELL|SCRAP|DONATE", message = "处置方式仅支持 SELL(出售)/SCRAP(报废)/DONATE(捐赠)")
        String method,
        @NotNull(message = "清理收入不能为空（无收入填 0）")
        @DecimalMin(value = "0", message = "清理收入不能小于 0")
        BigDecimal income,
        @Size(max = 200, message = "凭证说明长度不能超过 200")
        String remark) {
}
