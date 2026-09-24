package com.erp.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 折旧计提期间实体（表 ast_depreciation）。
 * <p>
 * 每个会计期间（yyyyMM）至多一行：run 时懒创建并置 OPEN，计提成功后置 CLOSED；
 * CLOSED 期间禁止重复运行（uk_period + 状态检查 + FOR UPDATE 行锁三重保障）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_depreciation")
public class AstDepreciation extends BaseEntity {

    /** 期间状态：开放（尚未计提） */
    public static final String STATUS_OPEN = "OPEN";
    /** 期间状态：已计提关闭（重复运行拒绝） */
    public static final String STATUS_CLOSED = "CLOSED";

    /** 会计期间（yyyyMM，全局唯一） */
    private String period;

    /** 状态：OPEN-开放 CLOSED-已计提关闭 */
    private String status;

    /** 本期间汇总折旧凭证 ID（fin_voucher.id；无可计提资产时为空） */
    private Long voucherId;

    /** 本期间计提资产数 */
    private Integer assetCount;

    /** 本期间计提总额 */
    private BigDecimal totalAmount;
}
