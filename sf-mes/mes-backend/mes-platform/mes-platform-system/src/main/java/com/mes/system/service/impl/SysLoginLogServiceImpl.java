package com.mes.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.system.dto.log.LoginLogQuery;
import com.mes.system.dto.log.LoginLogVO;
import com.mes.system.entity.SysLoginLog;
import com.mes.system.mapper.SysLoginLogMapper;
import com.mes.system.service.SysLoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 登录日志查询服务实现（写入见 {@code LoginLogListener}）。
 */
@Service
@RequiredArgsConstructor
public class SysLoginLogServiceImpl implements SysLoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    @Override
    public PageResult<LoginLogVO> page(LoginLogQuery query) {
        // 注意：MP 的 like(condition, col, val) 第三参会被立即求值，先收敛 null 再传参，避免 NPE
        String username = query.getUsername() == null ? null : query.getUsername().trim();
        String event = query.getEvent() == null ? null : query.getEvent().trim().toUpperCase();
        String ip = query.getIp() == null ? null : query.getIp().trim();
        var wrapper = Wrappers.lambdaQuery(SysLoginLog.class)
                .like(username != null && !username.isBlank(), SysLoginLog::getUsername, username)
                .eq(event != null && !event.isBlank(), SysLoginLog::getEvent, event)
                .eq(query.getStatus() != null, SysLoginLog::getStatus, query.getStatus())
                .eq(ip != null && !ip.isBlank(), SysLoginLog::getIp, ip)
                .ge(query.getBeginTime() != null, SysLoginLog::getCreateTime, query.getBeginTime())
                .le(query.getEndTime() != null, SysLoginLog::getCreateTime, query.getEndTime())
                .orderByDesc(SysLoginLog::getId);
        return PageUtils.of(loginLogMapper, wrapper, query.getPageNum(), query.getPageSize(), LoginLogVO::from);
    }
}
