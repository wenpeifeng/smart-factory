package com.mes.execution.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.execution.dto.vo.WipVO;
import com.mes.execution.entity.ExeWip;
import com.mes.execution.mapper.ExeWipMapper;
import com.mes.execution.service.WipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * WIP 查询服务实现。
 */
@Service
@RequiredArgsConstructor
public class WipServiceImpl implements WipService {

    private final ExeWipMapper wipMapper;

    @Override
    public PageResult<WipVO> page(long pageNum, long pageSize, String keyword, Long workorderId) {
        var wrapper = Wrappers.lambdaQuery(ExeWip.class)
                .like(StringUtils.hasText(keyword), ExeWip::getWoNo, keyword)
                .eq(workorderId != null, ExeWip::getWorkorderId, workorderId)
                .orderByDesc(ExeWip::getId);
        return PageUtils.of(wipMapper, wrapper, pageNum, pageSize, WipVO::from);
    }
}
