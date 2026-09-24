package com.mes.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.dto.vo.BaseWorkcenterVO;
import com.mes.basedata.entity.BaseWorkcenter;
import com.mes.basedata.mapper.BaseWorkcenterMapper;
import com.mes.basedata.service.BaseWorkcenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工作中心服务实现。
 */
@Service
@RequiredArgsConstructor
public class BaseWorkcenterServiceImpl implements BaseWorkcenterService {

    private final BaseWorkcenterMapper workcenterMapper;

    @Override
    public List<BaseWorkcenterVO> listEnabled() {
        List<BaseWorkcenter> list = workcenterMapper.selectList(Wrappers.lambdaQuery(BaseWorkcenter.class)
                .eq(BaseWorkcenter::getStatus, "ENABLED")
                .orderByAsc(BaseWorkcenter::getCode));
        return list.stream().map(BaseWorkcenterVO::from).toList();
    }
}
