package com.mes.framework.mybatis;

import java.util.Locale;

/**
 * 数据范围枚举（docs/集团组织与数据权限设计.md 第 3 章，对标金蝶五档）。
 * <p>
 * 五档均已实现 SQL 注入（{@link DataScopeInnerInterceptor}）：
 * ALL 不注入；ORG_AND_CHILD/ORG_ONLY 注入 org_id 条件；
 * DEPT_ONLY 注入 org_id AND dept_id（仅有 dept_id 列的表加 dept 条件）；
 * SELF 注入 create_by = 当前用户。
 */
public enum DataScope {

    /** 全部数据：不注入条件 */
    ALL,

    /** 本组织及下级：org_id IN (当前组织及递归子组织) */
    ORG_AND_CHILD,

    /** 仅本组织：org_id = 当前组织 */
    ORG_ONLY,

    /** 仅本部门：org_id = 当前组织 AND dept_id = 用户部门（无 dept_id 列的表仅组织级过滤） */
    DEPT_ONLY,

    /** 仅本人：create_by = 当前用户 */
    SELF;

    /**
     * 字符串安全解析（sys_user.data_scope 列存储枚举名）。
     *
     * @param value 枚举名字符串（可空）
     * @return 对应枚举；无法识别返回 null（调用方按 ALL 处理并告警）
     */
    public static DataScope of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
