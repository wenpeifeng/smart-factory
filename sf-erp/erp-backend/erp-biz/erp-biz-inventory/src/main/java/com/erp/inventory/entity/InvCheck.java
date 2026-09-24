package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 库存盘点单（头）—— 按仓库生成库存快照，录实盘后差异调平。
 * <p>
 * 状态机：0 盘点中 → 1 已完成（单向）。完成时对每一条差异生成
 * {@link com.erp.inventory.constant.InvBizType#ADJUST} 流水调平库存；
 * 未录实盘的行视为"未盘点"，不参与调平。
 * V421 起带来源列：MANUAL 手工 / CYCLE ABC 循环盘点按分级频度自动生成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_check")
public class InvCheck extends BaseEntity {

    /** 状态：盘点中（快照已生成，可录实盘） */
    public static final int STATUS_OPEN = 0;
    /** 状态：已完成（差异已生成 ADJUST 流水并调平库存） */
    public static final int STATUS_DONE = 1;

    /** 来源：手工盘点（默认） */
    public static final String SOURCE_MANUAL = "MANUAL";
    /** 来源：ABC 循环盘点按分级频度自动生成 */
    public static final String SOURCE_CYCLE = "CYCLE";

    /** 单号（CK+yyyyMMdd+4位流水） */
    private String no;

    /** 盘点仓库 ID */
    private Long warehouseId;

    /** 状态：0盘点中 1已完成 */
    private Integer status;

    /** 来源：MANUAL 手工 / CYCLE 循环盘点自动（V421） */
    private String source;

    /** 完成（调平）时间 */
    private LocalDateTime completeTime;

    /** 备注 */
    private String remark;
}
