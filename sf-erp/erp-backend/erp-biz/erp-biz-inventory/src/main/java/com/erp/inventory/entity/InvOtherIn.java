package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 其他入库单（头）—— 无来源的手工入库单据（领用退回/盘盈/赠品等）。
 * <p>
 * 状态机：0 草稿 → 1 已审核（单向，审核时逐行过账库存）；
 * 审核过账调用 {@link com.erp.inventory.service.StockService#changeStock}，
 * 类型 {@link com.erp.inventory.constant.InvBizType#OTH_IN}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_other_in")
public class InvOtherIn extends BaseEntity {

    /** 单据状态：草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 单据状态：已审核（库存已过账） */
    public static final int STATUS_AUDITED = 1;

    /** 单号（OI+yyyyMMdd+4位流水） */
    private String no;

    /** 入库仓库 ID */
    private Long warehouseId;

    /** 入库原因（领用退回/盘盈/赠品/样品/其他等，自由文本） */
    private String reason;

    /** 状态：0草稿 1已审核 */
    private Integer status;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 备注 */
    private String remark;
}
