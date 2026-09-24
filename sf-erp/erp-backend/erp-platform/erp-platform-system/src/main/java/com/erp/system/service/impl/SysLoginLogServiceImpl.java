package com.erp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.system.dto.log.LoginLogQuery;
import com.erp.system.dto.log.LoginLogVO;
import com.erp.system.entity.SysLoginLog;
import com.erp.system.mapper.SysLoginLogMapper;
import com.erp.system.service.SysLoginLogService;
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
