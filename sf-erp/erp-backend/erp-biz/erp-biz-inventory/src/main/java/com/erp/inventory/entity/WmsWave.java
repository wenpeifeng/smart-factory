package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * WMS 拣选波次（V421）—— 多张已审核出库单合并为一波，按拣选路径序统一执行。
 * <p>
 * 状态机：0 建波（任务已生成并入波）→ 10 拣选中（首个任务开始/完成时推进）→
 * 20 完成（波内全部 PICK 任务完成后）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_wave")
public class WmsWave extends BaseEntity {

    /** 状态：建波（任务已生成，未开始拣选） */
    public static final int STATUS_CREATED = 0;
    /** 状态：拣选中 */
    public static final int STATUS_PICKING = 10;
    /** 状态：完成（波内任务全部完成） */
    public static final int STATUS_DONE = 20;

    /** 波次号（W+yyyyMMdd+4位流水） */
    private String waveNo;

    /** 仓库 ID */
    private Long warehouseId;

    /** 状态：0建波 10拣选中 20完成 */
    private Integer status;

    /** 完成时间（全部任务完成时写） */
    private LocalDateTime finishTime;

    /** 备注 */
    private String remark;
}
