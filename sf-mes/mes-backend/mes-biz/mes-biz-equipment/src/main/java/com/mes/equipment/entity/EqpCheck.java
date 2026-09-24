package com.mes.equipment.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备点检记录实体（表 eqp_check）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("eqp_check")
public class EqpCheck extends BaseEntity {
    /** 点检单号（DJ+yyyyMMdd+4位流水） */
    private String checkNo;
    /** 设备 ID */
    private Long deviceId;
    /** 点检人 */
    private String checkerName;
    /** 点检结果：OK 正常 / NG 异常 */
    private String result;
    /** 点检时间 */
    private LocalDateTime checkTime;
    /** 异常说明/备注 */
    private String remark;
}
