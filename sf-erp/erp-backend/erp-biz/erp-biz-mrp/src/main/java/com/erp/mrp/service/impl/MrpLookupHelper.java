package com.erp.mrp.service.impl;

import com.erp.mrp.dto.vo.MaterialBriefVO;
import com.erp.mrp.dto.vo.SupplierBriefVO;
import com.erp.mrp.dto.vo.WarehouseBriefVO;
import com.erp.mrp.mapper.MrpIntegrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 跨模块只读查找辅助 —— 集中封装"约定表简要信息 → Map 索引"的批量查询，
 * 供各 Service 做 VO 回显（物料编码/名称、仓库名、供应商名），避免 N+1 查询。
 */
@Component
@RequiredArgsConstructor
public class MrpLookupHelper {

    private final MrpIntegrationMapper integrationMapper;

    /**
     * 批量查物料信息并按 ID 建索引。
     *
     * @param ids 物料ID集合（空返回空 Map）
     * @return id → 物料简要信息
     */
    public Map<Long, MaterialBriefVO> materialMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return integrationMapper.selectMaterials(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(MaterialBriefVO::getId, Function.identity()));
    }

    /**
     * 查全部仓库并按 ID 建名称索引。
     *
     * @return id → 仓库名称
     */
    public Map<Long, String> warehouseNames() {
        return integrationMapper.selectWarehouses().stream()
                .collect(Collectors.toMap(WarehouseBriefVO::getId, WarehouseBriefVO::getName));
    }

    /**
     * 批量查供应商名称并按 ID 建索引。
     *
     * @param ids 供应商ID集合（空返回空 Map）
     * @return id → 供应商名称
     */
    public Map<Long, String> supplierNames(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<SupplierBriefVO> list = integrationMapper.selectSuppliers(null);
        return list.stream()
                .filter(s -> ids.contains(s.getId()))
                .collect(Collectors.toMap(SupplierBriefVO::getId, SupplierBriefVO::getName));
    }
}
