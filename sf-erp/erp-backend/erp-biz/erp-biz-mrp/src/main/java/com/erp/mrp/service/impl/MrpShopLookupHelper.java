package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.mrp.entity.MrpOperation;
import com.erp.mrp.entity.MrpWorkCenter;
import com.erp.mrp.mapper.MrpOperationMapper;
import com.erp.mrp.mapper.MrpWorkCenterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 二期车间作业只读查找辅助 —— 工作中心/工序的批量 Map 索引查询，
 * 供工艺路线、派工看板、能力计划等 Service 做 VO 回显，避免 N+1。
 */
@Component
@RequiredArgsConstructor
public class MrpShopLookupHelper {

    private final MrpWorkCenterMapper workCenterMapper;
    private final MrpOperationMapper operationMapper;

    /**
     * 批量查工作中心并按 ID 建索引。
     *
     * @param ids 工作中心ID集合（空返回空 Map）
     * @return id → 工作中心实体
     */
    public Map<Long, MrpWorkCenter> workCenterMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return workCenterMapper.selectBatchIds(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(MrpWorkCenter::getId, Function.identity()));
    }

    /**
     * 查全部启用状态工作中心（选择器/看板分组用）。
     *
     * @return 工作中心列表
     */
    public java.util.List<MrpWorkCenter> listWorkCenters() {
        return workCenterMapper.selectList(Wrappers.lambdaQuery(MrpWorkCenter.class)
                .orderByAsc(MrpWorkCenter::getCode));
    }

    /**
     * 批量查工序并按 ID 建索引。
     *
     * @param ids 工序ID集合（空返回空 Map）
     * @return id → 工序实体
     */
    public Map<Long, MrpOperation> operationMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return operationMapper.selectBatchIds(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(MrpOperation::getId, Function.identity()));
    }
}
