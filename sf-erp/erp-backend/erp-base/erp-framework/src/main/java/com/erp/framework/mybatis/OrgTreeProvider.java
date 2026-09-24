package com.erp.framework.mybatis;

import java.util.Set;

/**
 * 组织树提供者 SPI —— 数据权限 ORG_AND_CHILD 档需要"当前组织及递归子组织"ID 集合。
 * <p>
 * framework 层不持有组织表（依赖方向约束），由 erp-platform-system 提供实现：
 * V1041 起 org_factory 带 parent_id 父子层级，实现为内存递归收集 + Redis 缓存
 * （{@code org:tree:{orgId}}，TTL 10 分钟兜底组织层级变更）。
 * org_org 集团树落地后替换数据源即可，本接口不变。
 */
public interface OrgTreeProvider {

    /**
     * 取当前组织及其全部下级组织 ID 集合（至少包含自身）。
     *
     * @param orgId 当前组织 ID
     * @return 组织 ID 集合（含 orgId 本身；组织不存在时仅返回自身）
     */
    Set<Long> getOrgAndChildIds(Long orgId);
}
