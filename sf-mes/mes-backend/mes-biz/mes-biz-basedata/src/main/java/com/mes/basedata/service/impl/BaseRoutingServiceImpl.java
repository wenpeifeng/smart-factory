package com.mes.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseRouting;
import com.mes.basedata.entity.BaseRoutingStep;
import com.mes.basedata.mapper.BaseRoutingMapper;
import com.mes.basedata.mapper.BaseRoutingStepMapper;
import com.mes.basedata.service.BaseRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工艺路线服务实现。
 */
@Service
@RequiredArgsConstructor
public class BaseRoutingServiceImpl implements BaseRoutingService {

    private final BaseRoutingMapper routingMapper;
    private final BaseRoutingStepMapper routingStepMapper;

    @Override
    public BaseRouting findActive(Long materialId) {
        return routingMapper.selectOne(Wrappers.lambdaQuery(BaseRouting.class)
                .eq(BaseRouting::getMaterialId, materialId)
                .eq(BaseRouting::getStatus, "ACTIVE")
                .last("LIMIT 1"));
    }

    @Override
    public List<BaseRoutingStep> listSteps(Long routingId) {
        return routingStepMapper.selectList(Wrappers.lambdaQuery(BaseRoutingStep.class)
                .eq(BaseRoutingStep::getRoutingId, routingId)
                .orderByAsc(BaseRoutingStep::getSeqNo));
    }
}
