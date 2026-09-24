package com.mes.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.system.dto.log.OperLogQuery;
import com.mes.system.dto.log.OperLogVO;
import com.mes.system.entity.SysOperLog;
import com.mes.system.mapper.SysOperLogMapper;
import com.mes.system.service.SysOperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 操作日志查询服务实现（写入由 OperLogAspect 直接走 Mapper 落库）。
 */
@Service
@RequiredArgsConstructor
public class SysOperLogServiceImpl implements SysOperLogService {

    private final SysOperLogMapper operLogMapper;

    @Override
    public PageResult<OperLogVO> page(OperLogQuery query) {
        // 注意：MP 的 like(condition, col, val) 第三参会被立即求值，先收敛 null 再传参，避免 NPE
        String operatorName = query.getOperatorName() == null ? null : query.getOperatorName().trim();
        var wrapper = Wrappers.lambdaQuery(SysOperLog.class)
                .eq(query.getModule() != null && !query.getModule().isBlank(),
                        SysOperLog::getModule, query.getModule())
                .like(operatorName != null && !operatorName.isBlank(),
                        SysOperLog::getOperatorName, operatorName)
                .eq(query.getStatus() != null, SysOperLog::getStatus, query.getStatus())
                .ge(query.getBeginTime() != null, SysOperLog::getCreateTime, query.getBeginTime())
                .le(query.getEndTime() != null, SysOperLog::getCreateTime, query.getEndTime())
                .orderByDesc(SysOperLog::getId);
        return PageUtils.of(operLogMapper, wrapper, query.getPageNum(), query.getPageSize(), OperLogVO::from);
    }
}
