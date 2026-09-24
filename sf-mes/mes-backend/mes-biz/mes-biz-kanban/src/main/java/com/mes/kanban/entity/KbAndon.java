package com.mes.kanban.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 安灯异常呼叫实体（表 kb_andon）。
 * <p>状态机：OPEN → RESPOND → CLOSED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@com.baomidou.mybatisplus.annotation.TableName("kb_andon")
public class KbAndon extends BaseEntity {
    /** 呼叫单号（AD+yyyyMMdd+4位流水） */
    private String callNo;
    /** 工作中心 ID */
    private Long workcenterId;
    /** 设备 ID（设备类异常） */
    private Long deviceId;
    /** 类型：DEVICE 设备 / MATERIAL 物料 / QUALITY 质量 / OTHER 其他 */
    private String type;
    /** 呼叫人 */
    private String callerName;
    /** 异常描述 */
    private String message;
    /** 状态：OPEN / RESPOND / CLOSED */
    private String status;
    /** 呼叫时间 */
    private LocalDateTime callTime;
    /** 响应时间 */
    private LocalDateTime respondTime;
    /** 响应人 */
    private String responder;
    /** 关闭时间 */
    private LocalDateTime closeTime;
    /** 关闭说明 */
    private String closeRemark;
}
