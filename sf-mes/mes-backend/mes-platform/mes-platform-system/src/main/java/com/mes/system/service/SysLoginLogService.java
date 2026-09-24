package com.mes.system.service;

import com.mes.common.result.PageResult;
import com.mes.system.dto.log.LoginLogQuery;
import com.mes.system.dto.log.LoginLogVO;

/**
 * 登录日志查询服务（写入由 LoginLogListener 监听登录事件落库）。
 */
public interface SysLoginLogService {

    /**
     * 分页查询登录日志。
     *
     * @param query 查询条件（账号/事件/结果/IP/时间段）
     * @return 日志 VO 分页（按时间倒序）
     */
    PageResult<LoginLogVO> page(LoginLogQuery query);
}
