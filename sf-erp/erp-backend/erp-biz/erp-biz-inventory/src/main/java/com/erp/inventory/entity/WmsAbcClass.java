package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 物料 ABC 分级（V421）—— 循环盘点频度依据。
 * <p>
 * 分级规则：近 90 天出库频次（inv_movement 出库流水行数）降序排名，
 * 前 20% 为 A（每月盘点）、随后 30% 为 B（每季）、其余 50% 为 C（每年）。
 * 每次重算软删旧记录、写入新记录（现行分级 = deleted=0 的记录集）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_abc_class")
public class WmsAbcClass extends BaseEntity {

    /** 分级：A（高频，前 20%） */
    public static final String LEVEL_A = "A";
    /** 分级：B（中频，随后 30%） */
    public static final String LEVEL_B = "B";
    /** 分级：C（低频，其余 50%） */
    public static final String LEVEL_C = "C";

    /** 物料 ID */
    private Long materialId;

    /** 分级：A/B/C */
    private String level;

    /** 近 90 天出库频次（出库流水行数） */
    private Long freq;

    /** 分级计算日期 */
    private LocalDate calcDate;
}
