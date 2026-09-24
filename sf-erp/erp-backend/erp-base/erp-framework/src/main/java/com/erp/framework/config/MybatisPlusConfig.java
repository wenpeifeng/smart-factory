package com.erp.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.erp.framework.context.OrgContextHolder;
import com.erp.framework.mybatis.DataScopeInnerInterceptor;
import com.erp.framework.mybatis.OrgTreeProvider;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis-Plus 配置。
 * <ul>
 *   <li>拦截器链（顺序敏感）：多租户 → 数据权限 → 分页。租户/数据权限改写 SQL 后
 *       再由分页插件加 LIMIT/COUNT（MP 官方要求多租户在分页之前）；</li>
 *   <li>分页插件：物理分页（LIMIT），并自动适配 MySQL 方言；</li>
 *   <li>审计字段自动填充：插入/更新时自动写入 create_by / create_time / update_by / update_time，
 *       以及 tenant_id / org_id（取 {@link OrgContextHolder}，共享表 org_id 固定 0），
 *       业务代码不再手写这些赋值。</li>
 * </ul>
 */
@Configuration
public class MybatisPlusConfig {

    /** 实体类 → 表名缓存（org 填充按表语义判定） */
    private static final ConcurrentHashMap<Class<?>, String> TABLE_NAME_CACHE = new ConcurrentHashMap<>();

    /**
     * MP 拦截器链：多租户（{@link TenantConfig} 提供处理器）→ 数据权限 → 分页。
     * 单页上限 500 条，防止误传 pageSize=100000 拖垮数据库。
     *
     * @param tenantLineHandler 租户条件处理器（TenantConfig）
     * @param orgTreeProviders  组织树 SPI（由 erp-platform-system 提供，可缺省）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantLineHandler tenantLineHandler,
                                                         ObjectProvider<OrgTreeProvider> orgTreeProviders) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 多租户：自动 WHERE tenant_id = ? / INSERT 补租户列（共享表白名单跳过）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
        // 2. 数据权限：组织隔离表按用户数据范围注入 org_id 条件（SELECT）
        // 传 ObjectProvider 本身：拦截器查询期再解析 OrgTreeProvider（装配期解析会与
        // sqlSessionFactory 形成循环依赖：interceptor <- provider <- mapper <- sqlSessionFactory）
        interceptor.addInnerInterceptor(new DataScopeInnerInterceptor(orgTreeProviders));
        // 3. 分页：必须放最后
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /**
     * 审计字段自动填充处理器。
     * <p>
     * createBy/updateBy 取登录用户上下文：JWT 过滤器（erp-system）认证成功后把
     * 用户 ID（Long）放入 SecurityContext 的 principal；未登录场景（定时任务、
     * 系统初始化、登录接口本身）填 0 表示系统。
     * <p>
     * tenantId 取 {@link OrgContextHolder#getTenantId()}（无上下文默认 1000）；
     * orgId 按表语义：共享表/无组织语义表填 0，其余填当前组织（无上下文默认 1）。
     * 显式赋值优先（strict 填充仅在字段为 null 时生效），便于管理门户跨租户建数据。
     * <p>
     * 注意：此处以 {@code instanceof Long} 解耦读取，不得引入 erp-system 的类型
     * （依赖方向只能向下），principal 类型约定见 JwtAuthFilter 的 Javadoc。
     */
    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "createBy", Long.class, currentUserId());
                this.strictInsertFill(metaObject, "updateBy", Long.class, currentUserId());
                // 多租户 + 组织：与库列默认值同语义
                this.strictInsertFill(metaObject, "tenantId", Long.class, OrgContextHolder.getTenantId());
                this.strictInsertFill(metaObject, "orgId", Long.class, resolveOrgId(metaObject));
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                this.strictUpdateFill(metaObject, "updateBy", Long.class, currentUserId());
            }

            /** 获取当前登录用户 ID，统一走 SecurityUtils（未登录返回 0=系统操作） */
            private Long currentUserId() {
                return com.erp.framework.security.SecurityUtils.getCurrentUserId();
            }

            /** 按实体映射表判定 org 填充值：共享/无组织语义表 0，否则当前组织 */
            private Long resolveOrgId(MetaObject metaObject) {
                Object original = metaObject.getOriginalObject();
                if (original == null) {
                    return OrgContextHolder.getOrgId();
                }
                String table = TABLE_NAME_CACHE.computeIfAbsent(original.getClass(), MybatisPlusConfig::resolveTableName);
                return TenantConfig.isOrgZeroTable(table) ? 0L : OrgContextHolder.getOrgId();
            }
        };
    }

    /**
     * 解析实体映射表名：优先 @TableName，缺省按 MP 默认规则（类名驼峰转下划线）。
     *
     * @param entityClass 实体类
     * @return 小写表名
     */
    private static String resolveTableName(Class<?> entityClass) {
        TableName annotation = entityClass.getAnnotation(TableName.class);
        if (annotation != null && !annotation.value().isBlank()) {
            return annotation.value().trim().toLowerCase(Locale.ROOT);
        }
        String simple = entityClass.getSimpleName();
        StringBuilder sb = new StringBuilder(simple.length() + 8);
        for (int i = 0; i < simple.length(); i++) {
            char ch = simple.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
