package com.erp.system.dto.log;

import com.erp.system.entity.SysLoginLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志响应 VO。
 */
@Data
public class LoginLogVO {

    /** 日志 ID */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 用户 ID（失败为 null） */
    private Long userId;

    /** 事件：LOGIN / LOGOUT */
    private String event;

    /** 结果：1-成功 0-失败 */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;

    /** 客户端 IP */
    private String ip;

    /** 浏览器 UA */
    private String userAgent;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 租户 ID */
    private Long tenantId;

    /** 登录/登出时间 */
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param entity 日志实体
     * @return VO
     */
    public static LoginLogVO from(SysLoginLog entity) {
        LoginLogVO vo = new LoginLogVO();
        vo.setId(entity.getId());
        vo.setUsername(entity.getUsername());
        vo.setUserId(entity.getUserId());
        vo.setEvent(entity.getEvent());
        vo.setStatus(entity.getStatus());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setIp(entity.getIp());
        vo.setUserAgent(entity.getUserAgent());
        vo.setCostMs(entity.getCostMs());
        vo.setTenantId(entity.getTenantId());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
