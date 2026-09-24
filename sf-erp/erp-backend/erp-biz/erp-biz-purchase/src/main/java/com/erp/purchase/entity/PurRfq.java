package com.erp.purchase.entity;

import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 询比价单主表实体（pur_rfq）。
 * <p>
 * 状态机：0询价中 -> 10已定标；0询价中可取消为 30已取消（终态）。
 * 已定标禁止修改/删除/再录报价（防中标结果被篡改）；
 * 下推采购订单仅在已定标且明细未下推时允许（PurRfqServiceImpl#toOrders）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurRfq extends BaseEntity {

    /** 状态：询价中 */
    public static final int STATUS_QUOTING = 0;
    /** 状态：已定标 */
    public static final int STATUS_AWARDED = 10;
    /** 状态：已取消（终态） */
    public static final int STATUS_CANCELLED = 30;

    /** 询比价单号（IN+yyyyMMdd+4位流水，Redis 生成，唯一） */
    private String rfqNo;

    /** 来源请购单 ID（可空=独立询价） */
    private Long requisitionId;

    /** 报价截止日期 */
    private LocalDate deadline;

    /** 状态：0询价中 10已定标 30已取消 */
    private Integer status;

    /** 定标人（sys_user.id） */
    private Long awardBy;

    /** 定标时间 */
    private LocalDateTime awardTime;

    /** 备注 */
    private String remark;
}
