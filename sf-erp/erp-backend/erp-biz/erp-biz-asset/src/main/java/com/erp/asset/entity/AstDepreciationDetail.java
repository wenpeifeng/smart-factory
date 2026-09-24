package com.erp.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 折旧计提明细实体（表 ast_depreciation_detail）。
 * <p>
 * 期间 × 资产 一行（uk_period_asset），记录该资产当月折旧额及其所属汇总凭证；
 * 凭证号与期间一致（同期间所有明细共用一张汇总凭证）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_depreciation_detail")
public class AstDepreciationDetail extends BaseEntity {

    /** 期间 ID（ast_depreciation.id） */
    private Long periodId;

    /** 资产 ID（ast_asset.id） */
    private Long assetId;

    /** 本月折旧额 =（原值 − 残值）/ 预计年限月，HALF_UP 保留 2 位 */
    private BigDecimal monthAmount;

    /** 折旧凭证 ID（fin_voucher.id，同期间共用汇总凭证） */
    private Long voucherId;
}
