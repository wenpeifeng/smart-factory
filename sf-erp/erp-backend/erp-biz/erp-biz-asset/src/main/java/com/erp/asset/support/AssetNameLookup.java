package com.erp.asset.support;

import com.erp.asset.mapper.AstQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 跨模块名称查询（只读 sys_dept / base_supplier，不 import 对方代码 —— 并行协作约定）。
 * <p>
 * 对方表尚未落库（或迁移未执行）时 SQL 会抛异常，此处降级为"空映射"：
 * 前端展示 ID 占位（如 "部门#3"），落库后无需改动自动恢复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetNameLookup {

    private final AstQueryMapper queryMapper;

    /**
     * 批量解析部门名称。
     *
     * @param deptIds 部门 ID 集合
     * @return id → name；入参为空或表不可用时返回空 Map
     */
    public Map<Long, String> deptNames(List<Long> deptIds) {
        return safeNames(deptIds, queryMapper::selectDeptNames);
    }

    /**
     * 批量解析供应商名称。
     *
     * @param supplierIds 供应商 ID 集合
     * @return id → name；入参为空或表不可用时返回空 Map
     */
    public Map<Long, String> supplierNames(List<Long> supplierIds) {
        return safeNames(supplierIds, queryMapper::selectSupplierNames);
    }

    /** 统一执行 + 异常降级（base_supplier / sys_dept 未建表时返回空映射） */
    private Map<Long, String> safeNames(List<Long> ids, Function<List<Long>, List<Map<String, Object>>> query) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        try {
            return query.apply(ids).stream().collect(Collectors.toMap(
                    row -> ((Number) row.get("id")).longValue(),
                    row -> String.valueOf(row.get("name"))));
        } catch (DataAccessException e) {
            log.warn("名称查询降级（表可能未落库）：{}", e.getLocalizedMessage());
            return Map.of();
        }
    }
}
