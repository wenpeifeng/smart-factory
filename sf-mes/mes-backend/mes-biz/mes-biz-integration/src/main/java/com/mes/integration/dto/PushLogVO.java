package com.mes.integration.dto;

import com.mes.integration.erp.push.ErpPushLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ERP 回传推送日志响应 VO。
 */
@Data
public class PushLogVO {
    private Long id;
    private String bizType;
    private Long bizId;
    private String bizNo;
    private String payload;
    private String status;
    private Integer retryCount;
    private Integer maxRetry;
    private String lastError;
    private LocalDateTime pushTime;
    private LocalDateTime successTime;
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param e 推送日志实体
     * @return VO
     */
    public static PushLogVO from(ErpPushLog e) {
        PushLogVO vo = new PushLogVO();
        vo.setId(e.getId());
        vo.setBizType(e.getBizType());
        vo.setBizId(e.getBizId());
        vo.setBizNo(e.getBizNo());
        vo.setPayload(e.getPayload());
        vo.setStatus(e.getStatus());
        vo.setRetryCount(e.getRetryCount());
        vo.setMaxRetry(e.getMaxRetry());
        vo.setLastError(e.getLastError());
        vo.setPushTime(e.getPushTime());
        vo.setSuccessTime(e.getSuccessTime());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
