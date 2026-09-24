package com.mes.kanban.dto.vo;

import com.mes.kanban.entity.KbAndon;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安灯呼叫 VO。
 */
@Data
public class AndonVO {
    private Long id;
    private String callNo;
    private Long workcenterId;
    private String workcenterName;
    private Long deviceId;
    private String type;
    private String callerName;
    private String message;
    private String status;
    private LocalDateTime callTime;
    private LocalDateTime respondTime;
    private String responder;
    private LocalDateTime closeTime;
    private String closeRemark;

    /**
     * 实体转 VO。
     *
     * @param e 安灯实体
     * @return VO
     */
    public static AndonVO from(KbAndon e) {
        AndonVO vo = new AndonVO();
        vo.setId(e.getId());
        vo.setCallNo(e.getCallNo());
        vo.setWorkcenterId(e.getWorkcenterId());
        vo.setDeviceId(e.getDeviceId());
        vo.setType(e.getType());
        vo.setCallerName(e.getCallerName());
        vo.setMessage(e.getMessage());
        vo.setStatus(e.getStatus());
        vo.setCallTime(e.getCallTime());
        vo.setRespondTime(e.getRespondTime());
        vo.setResponder(e.getResponder());
        vo.setCloseTime(e.getCloseTime());
        vo.setCloseRemark(e.getCloseRemark());
        return vo;
    }
}
