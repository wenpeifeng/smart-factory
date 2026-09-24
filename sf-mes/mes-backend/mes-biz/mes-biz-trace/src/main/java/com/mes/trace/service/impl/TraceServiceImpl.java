package com.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.plan.entity.PlanWorkorder;
import com.mes.plan.mapper.PlanWorkorderMapper;
import com.mes.trace.dto.vo.TraceNodeVO;
import com.mes.trace.entity.TraceLink;
import com.mes.trace.mapper.TraceLinkMapper;
import com.mes.trace.service.TraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 正反向追溯查询实现：都是对 trace_link 单表按编号索引查询，另一端即追溯结果。
 */
@Service
@RequiredArgsConstructor
public class TraceServiceImpl implements TraceService {

    private final TraceLinkMapper traceLinkMapper;
    private final PlanWorkorderMapper workorderMapper;

    @Override
    public List<TraceNodeVO> traceForward(String componentNo) {
        requireNo(componentNo);
        List<TraceLink> links = traceLinkMapper.selectList(Wrappers.lambdaQuery(TraceLink.class)
                .eq(TraceLink::getComponentNo, componentNo)
                .orderByDesc(TraceLink::getLinkTime));
        Map<Long, String> woNos = woNoMap(links);
        return links.stream()
                .map(l -> {
                    TraceNodeVO vo = TraceNodeVO.from(l, l.getProductNo(), "WO");
                    vo.setWoNo(woNos.get(l.getWorkorderId()));
                    return vo;
                })
                .toList();
    }

    @Override
    public List<TraceNodeVO> traceBackward(String productNo) {
        requireNo(productNo);
        // 兼容用工单号反查：WO: 前缀补全
        String effective = productNo;
        List<TraceLink> links = traceLinkMapper.selectList(Wrappers.lambdaQuery(TraceLink.class)
                .eq(TraceLink::getProductNo, effective));
        if (links.isEmpty() && !productNo.startsWith("WO:")) {
            links = traceLinkMapper.selectList(Wrappers.lambdaQuery(TraceLink.class)
                    .eq(TraceLink::getProductNo, "WO:" + productNo));
        }
        Map<Long, String> woNos = woNoMap(links);
        return links.stream()
                .map(l -> {
                    TraceNodeVO vo = TraceNodeVO.from(l, l.getComponentNo(), l.getComponentType());
                    vo.setWoNo(woNos.get(l.getWorkorderId()));
                    return vo;
                })
                .toList();
    }

    private Map<Long, String> woNoMap(List<TraceLink> links) {
        if (links.isEmpty()) {
            return Map.of();
        }
        return workorderMapper.selectBatchIds(
                        links.stream().map(TraceLink::getWorkorderId).distinct().toList())
                .stream().collect(Collectors.toMap(PlanWorkorder::getId, PlanWorkorder::getWoNo));
    }

    private void requireNo(String no) {
        if (!StringUtils.hasText(no)) {
            throw new BusinessException("请提供要追溯的批次号/SN/工单号");
        }
    }
}
