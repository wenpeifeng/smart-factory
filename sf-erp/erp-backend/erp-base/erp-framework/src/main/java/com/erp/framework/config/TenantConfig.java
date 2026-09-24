package com.erp.framework.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.erp.framework.context.OrgContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;
import java.util.Set;

/**
 * 多租户插件配置（docs/集团组织与数据权限设计.md v1.1 第 0 章）。
 * <p>
 * {@code TenantLineInnerInterceptor} 对所有 SQL（含 XML 手写 SQL）自动追加
 * {@code WHERE tenant_id = 当前租户}、INSERT 自动补租户列 —— 业务代码零侵入。
 * <p>
 * 共享表白名单（不追加租户条件，全租户可见）：
 * <ul>
 *   <li>主数据 base_*（物料/客商/仓库/单位/结算方式/库位/类别）；</li>
 *   <li>sys_menu / sys_dict_type / sys_dict_data（菜单、字典平台统一维护）；</li>
 *   <li>wf_definition（流程定义）、fin_subject（会计科目）、org_factory（工厂档案）；</li>
 *   <li>mrp_bom / mrp_bom_item（BOM，docs 0 章共享清单）；</li>
 *   <li>sys_tenant（租户登记表自身，管理门户跨租户维护）。</li>
 * </ul>
 * 拿不准的表一律按隔离处理（追加租户条件），有疑问在此登记共享。
 */
@Configuration
public class TenantConfig {

    /**
     * 租户插件忽略的共享表（小写；含 org_id=0 的共享主数据 + 平台级配置表）。
     * 与 V1012 迁移的 org_id DEFAULT 0 表清单保持同步维护。
     */
    public static final Set<String> TENANT_SHARED_TABLES = Set.of(
            // 主数据（base_*）
            "base_customer", "base_location", "base_material", "base_material_category",
            "base_settle_type", "base_supplier", "base_unit", "base_warehouse", "base_zone",
            // 平台共享配置
            "sys_menu", "sys_dict_type", "sys_dict_data",
            "wf_definition", "fin_subject", "org_factory",
            // BOM 共享（docs 0 章）
            "mrp_bom", "mrp_bom_item",
            // 租户登记表自身
            "sys_tenant",
            // Flyway 历史表（防手滑误伤）
            "flyway_schema_history"
    );

    /**
     * org 填充为 0 的表 = 租户共享表 + 租户内无组织语义的系统/流程运行时表
     * （sys_role/sys_dept/sys_user_role/sys_role_menu/sys_oper_log/wf_instance/wf_node/wf_task/wf_message，
     * 这些表的 org_id 列仅为 BaseEntity 映射兼容，无过滤语义）。
     */
    public static final Set<String> ORG_ZERO_TABLES = Set.of(
            "sys_role", "sys_dept", "sys_user_role", "sys_role_menu", "sys_oper_log",
            "wf_instance", "wf_node", "wf_task", "wf_message"
    );

    /**
     * 租户条件处理器：租户值取 OrgContextHolder（无上下文默认 1000）。
     *
     * @return TenantLineHandler（供 MybatisPlusInterceptor 装配租户插件）
     */
    @Bean
    public TenantLineHandler tenantLineHandler() {
        return new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(OrgContextHolder.getTenantId());
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // 系统库（information_schema 等）与 Flyway 历史表没有 tenant_id 列，必须跳过；
                // 业务模块用 information_schema 探测辅助表是否存在的 SQL 也会走到这里
                String normalized = normalize(tableName);
                if (normalized.startsWith("information_schema")
                        || normalized.startsWith("performance_schema")
                        || normalized.startsWith("mysql")
                        || normalized.startsWith("sys.")) {
                    return true;
                }
                return isTenantSharedTable(tableName);
            }
        };
    }

    /** 表名归一化：去反引号/空白并转小写 */
    private static String normalize(String tableName) {
        if (tableName == null) {
            return "";
        }
        return tableName.replace("`", "").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 是否租户共享表（不追加 tenant_id 条件）。
     *
     * @param tableName SQL 中出现的表名（可带反引号）
     * @return true 表示共享，租户插件跳过
     */
    public static boolean isTenantSharedTable(String tableName) {
        return TENANT_SHARED_TABLES.contains(normalize(tableName));
    }

    /**
     * 该表的 org_id 是否为共享语义 0（决定审计填充值：0=共享/无组织语义，否则当前组织）。
     *
     * @param tableName 实体映射表名
     * @return true 表示插入时 org_id 填 0
     */
    public static boolean isOrgZeroTable(String tableName) {
        String name = normalize(tableName);
        return TENANT_SHARED_TABLES.contains(name) || ORG_ZERO_TABLES.contains(name);
    }
}
