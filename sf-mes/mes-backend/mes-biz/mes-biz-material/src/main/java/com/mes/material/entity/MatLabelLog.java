package com.mes.material.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 标签打印日志实体（表 mat_label_log）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("mat_label_log")
public class MatLabelLog extends BaseEntity {
    /** 对象类型：BATCH 批次 / SN 序列号 / WO 工单 */
    private String objType;
    /** 对象 ID */
    private Long objId;
    /** 对象号 */
    private String objNo;
    /** 标签模板 */
    private String template;
    /** 打印份数 */
    private Integer copies;
    /** 打印人 */
    private String printer;
    /** 打印时间 */
    private LocalDateTime printTime;
}
