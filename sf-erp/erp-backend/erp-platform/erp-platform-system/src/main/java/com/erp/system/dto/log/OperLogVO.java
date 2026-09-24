package com.erp.system.dto.log;

import com.erp.system.entity.SysOperLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志响应 VO。
 */
@Data
public class OperLogVO {

    /** 日志 ID */
    private Long id;

    /** 功能模块 */
    private String module;

    /** 操作内容 */
    private String operation;

    /** 执行方法 */
    private String method;

    /** 请求方式 */
    private String requestMethod;

    /** 请求地址 */
    private String requestUrl;

    /** 请求参数（JSON） */
    private String requestParams;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人账号 */
    private String operatorName;

    /** 客户端 IP */
    private String ip;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 结果：1-成功 0-失败 */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;

    /** 操作时间 */
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param entity 日志实体
     * @return VO
     */
    public static OperLogVO from(SysOperLog entity) {
        OperLogVO vo = new OperLogVO();
        vo.setId(entity.getId());
        vo.setModule(entity.getModule());
        vo.setOperation(entity.getOperation());
        vo.setMethod(entity.getMethod());
        vo.setRequestMethod(entity.getRequestMethod());
        vo.setRequestUrl(entity.getRequestUrl());
        vo.setRequestParams(entity.getRequestParams());
        vo.setOperatorId(entity.getOperatorId());
        vo.setOperatorName(entity.getOperatorName());
        vo.setIp(entity.getIp());
        vo.setCostMs(entity.getCostMs());
        vo.setStatus(entity.getStatus());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
