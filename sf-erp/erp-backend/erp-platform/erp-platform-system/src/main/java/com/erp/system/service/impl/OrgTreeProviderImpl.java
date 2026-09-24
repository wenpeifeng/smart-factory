package com.erp.system.service.impl;

import com.erp.framework.mybatis.OrgTreeProvider;
import com.erp.system.dto.auth.OrgTreeNode;
import com.erp.system.mapper.OrgDirectoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组织树 SPI 实现（供 framework 的 DataScopeInnerInterceptor 解析"本组织及下级"）。
 * <p>
 * V1041 起 org_factory 具备 parent_id 父子层级：一次全量载入节点（组织档案小表），
 * 内存递归收集子树。Redis 缓存 {@code org:tree:{orgId} → "1,2,3"}，
 * <b>TTL 10 分钟</b> —— 组织层级由 erp-org 模块维护，framework/system 无其变更事件，
 * 短 TTL 即"组织变更最迟 10 分钟生效"的兜底（与 auth:scope 等缓存策略一致）；
 * org_org 集团树落地后替换数据源即可，缓存策略沿用。
 * <p>
 * 防环：递归深度上限 {@value #MAX_DEPTH}（脏数据 parent 成环时在深处截断，
 * 宁可少看不可越权）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrgTreeProviderImpl implements OrgTreeProvider {

    /** 组织树缓存键前缀：org:tree:{orgId}，值为本组织及下级 ID 的 CSV */
    static final String TREE_CACHE_KEY = "org:tree:";

    /** 缓存 TTL：短 TTL 兜底组织层级变更（无主动失效钩子） */
    private static final Duration TREE_CACHE_TTL = Duration.ofMinutes(10);

    /** 递归深度上限（防 parent 指向成环导致死循环） */
    private static final int MAX_DEPTH = 10;

    private final OrgDirectoryMapper orgDirectoryMapper;
    private final StringRedisTemplate redis;

    /**
     * 当前组织及全部下级组织 ID 集合（Redis 缓存 10 分钟）。
     *
     * @param orgId 当前组织 ID
     * @return 至少包含自身；组织不存在时同样仅返回自身（保守：宁可少看不可越权）
     */
    @Override
    public Set<Long> getOrgAndChildIds(Long orgId) {
        if (orgId == null) {
            return Set.of();
        }
        String cacheKey = TREE_CACHE_KEY + orgId;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            return parseCsv(cached, orgId);
        }
        Set<Long> ids = collectFromDb(orgId);
        redis.opsForValue().set(cacheKey, toCsv(ids), TREE_CACHE_TTL);
        return ids;
    }

    /** 全量载入节点并递归收集子树（缓存未命中时执行，一次 SQL） */
    private Set<Long> collectFromDb(Long orgId) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (OrgTreeNode node : orgDirectoryMapper.selectTreeNodes()) {
            if (node.getParentId() != null) {
                children.computeIfAbsent(node.getParentId(), k -> new ArrayList<>()).add(node.getId());
            }
        }
        Set<Long> ids = new LinkedHashSet<>();
        collect(orgId, children, ids, 0);
        if (ids.size() > 1) {
            log.debug("组织{}及下级共{}个：{}", orgId, ids.size(), ids);
        }
        return ids;
    }

    /** 深度优先递归：深度超限或节点重复（成环）即剪枝 */
    private void collect(Long orgId, Map<Long, List<Long>> children, Set<Long> ids, int depth) {
        if (depth >= MAX_DEPTH || !ids.add(orgId)) {
            if (!ids.contains(orgId)) {
                log.warn("组织树递归达深度上限{}（parent 疑似成环），orgId={} 截断", MAX_DEPTH, orgId);
            }
            return;
        }
        for (Long child : children.getOrDefault(orgId, List.of())) {
            collect(child, children, ids, depth + 1);
        }
    }

    /** CSV → ID 集合（容错：坏行忽略，至少保底自身） */
    private static Set<Long> parseCsv(String csv, Long selfId) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(selfId);
        if (csv != null && !csv.isBlank()) {
            for (String part : csv.split(",")) {
                try {
                    ids.add(Long.valueOf(part.trim()));
                } catch (NumberFormatException ignore) {
                    // 缓存值损坏：保留已解析部分 + 自身，下个 TTL 周期自愈
                }
            }
        }
        return ids;
    }

    /** ID 集合 → CSV */
    private static String toCsv(Set<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
