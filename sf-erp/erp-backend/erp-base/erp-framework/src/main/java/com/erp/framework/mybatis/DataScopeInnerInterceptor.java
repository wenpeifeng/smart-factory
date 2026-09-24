package com.erp.framework.mybatis;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.BaseMultiTableInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.erp.framework.context.OrgContextHolder;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 数据权限内拦截器（docs 第 3/4 章，五档数据范围，业务代码零侵入）。
 * <p>
 * 对 SELECT 语句中的组织隔离表自动注入条件（多表 JOIN 场景由
 * {@link BaseMultiTableInnerInterceptor} 保证条件挂到对应表的 ON/WHERE，带表限定名防列歧义）：
 * <ul>
 *   <li>ALL：不注入；</li>
 *   <li>ORG_AND_CHILD：{@code 表.org_id IN (当前组织及递归子组织)}（{@link OrgTreeProvider}，
 *       V1041 起 org_factory 有 parent_id 父子层级）；</li>
 *   <li>ORG_ONLY：{@code 表.org_id = 当前组织}；</li>
 *   <li>DEPT_ONLY：组织条件（同 ORG_ONLY）AND {@code 表.dept_id = 用户部门} ——
 *       dept 条件只对<b>实际存在 dept_id 列</b>的表注入；无该列的组织隔离表仅组织级过滤
 *       （debug 日志提示，宁可按组织收紧也不能因缺列放行到跨组织）；</li>
 *   <li>SELF：组织隔离表注入 {@code 表.create_by = 当前用户}。</li>
 * </ul>
 * <b>dept_id 列清单的判定</b>：不依赖实体反射（framework 无法感知 biz 模块实体，且实体
 * 有字段不等于表有列），启动后首次遇到 DEPT_ONLY 查询时用当前连接查
 * {@code information_schema.COLUMNS}（TABLE_SCHEMA=DATABASE() AND COLUMN_NAME='dept_id'）
 * 动态取得真实有列的表集合，进程内缓存 10 分钟 —— 以数据库真实结构为准，新表加列自动纳入。
 * <p>
 * 跳过条件（零开销直通）：无登录上下文（定时任务）、无数据范围、ALL、
 * super_admin 角色、{@code @InterceptorIgnore(dataPermission="true")} 标注的语句。
 * <p>
 * 组织隔离表白名单（前缀判定）：pur_ / sal_ / inv_ / fin_ / mrp_ / qc_ / cost_ / ast_ / eqp_ / hr_
 * 各业务单据表 + sys_user；共享主数据（base 前缀表、BOM、科目、菜单、字典等，org_id=0）、
 * 跨组织单据 org_transfer 族（调入方需可见）、系统/流程运行时表不注入。
 * 拿不准的表按"不注入"处理并在本类登记，避免误伤。
 */
@Slf4j
public class DataScopeInnerInterceptor extends BaseMultiTableInnerInterceptor implements InnerInterceptor {

    /** 超管角色编码（绕过数据权限，便于运维；JwtAuthFilter 以 ROLE_ 前缀放入 authorities） */
    public static final String SUPER_ADMIN_ROLE = "ROLE_super_admin";

    /** 组织隔离表前缀（数据范围注入对象） */
    private static final List<String> ORG_SCOPED_PREFIXES = List.of(
            "pur_", "sal_", "inv_", "fin_", "mrp_", "qc_", "cost_", "ast_", "eqp_", "hr_");

    /** 前缀命中但属于共享/跨组织语义、不注入的例外表 */
    private static final Set<String> ORG_SCOPED_EXCLUDES = Set.of(
            "fin_subject",      // 会计科目共享
            "fin_book",         // 账套为租户级档案（按租户隔离，不按组织）
            "fin_account",      // 资金账户租户级共享
            "mrp_bom", "mrp_bom_item" // BOM 共享
            // 注：org_transfer* 为跨组织调拨单，调入组织需可见，整族不注入
    );

    /** 组织隔离的精确表名（前缀覆盖不到的） */
    private static final Set<String> ORG_SCOPED_EXACT = Set.of("sys_user");

    /** dept_id 列表探测缓存时长（毫秒）：新表加减列最迟 10 分钟纳入/退出 DEPT_ONLY 过滤 */
    private static final long DEPT_TABLES_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);

    /**
     * 本次查询的注入参数（beforeQuery 算一次，buildTableExpression 逐表读取）。
     * 解析发生在当前线程内（parserSingle 同步调用），线程暂存安全。
     * 三个变量按档位互斥/组合：ORG_ 前缀与 DEPT_ONLY 置 orgIds；DEPT_ONLY 另置 deptId；
     * SELF 置 selfUserId（userId 由上下文提供，非空保证）。
     */
    private static final ThreadLocal<Set<Long>> CURRENT_ORG_IDS = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_DEPT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_SELF_USER_ID = new ThreadLocal<>();

    /**
     * 重入保护：ORG_AND_CHILD 取组织树时 provider 会发内部 SQL（selectTreeNodes），
     * 该查询再次进入本拦截器并在同线程再次请求组织树 → 无限递归 StackOverflow。
     * 进入即置位，嵌套查询（组织树/探测等内部 SQL）直通不注入。
     */
    private static final ThreadLocal<Boolean> IN_PROGRESS = new ThreadLocal<>();

    /**
     * 组织树 SPI 的延迟容器：拦截器在 sqlSessionFactory 装配链上实例化，
     * 若此刻直接解析 OrgTreeProviderImpl（其 Mapper 依赖 sqlSessionFactory）
     * 会形成循环依赖 —— 持有 ObjectProvider、首次查询时再解析（彼时单例已就绪）。
     */
    private final ObjectProvider<OrgTreeProvider> orgTreeProviders;

    /** 实际存在 dept_id 列的表名集合（information_schema 探测，缓存 10 分钟） */
    private volatile Set<String> deptColumnTables = Set.of();
    private volatile long deptTablesResolvedAt = 0L;

    public DataScopeInnerInterceptor(ObjectProvider<OrgTreeProvider> orgTreeProviders) {
        this.orgTreeProviders = orgTreeProviders;
    }

    /**
     * 查询前改写 SQL：满足注入条件时解析并追加组织/部门/本人条件。
     */
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        OrgContextHolder.OrgContext ctx = OrgContextHolder.get();
        if (ctx == null || ctx.dataScope() == null) {
            return; // 无登录上下文（定时任务/系统初始化）：不注入
        }
        if (InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId())) {
            return; // 语句级显式跳过（报表等跨组织聚合场景的逃生门）
        }
        if (Boolean.TRUE.equals(IN_PROGRESS.get())) {
            return; // 重入（组织树/列探测等本拦截器触发的内部 SQL）：直通
        }
        if (isSuperAdmin()) {
            return; // 超管绕过数据权限（设计文档第 5 章）
        }
        DataScope scope = DataScope.of(ctx.dataScope());
        if (scope == null) {
            log.warn("未知数据范围[{}]，用户{}按 ALL 处理（不注入组织条件）", ctx.dataScope(), ctx.userId());
            return;
        }
        Long orgId = ctx.orgId() != null ? ctx.orgId() : OrgContextHolder.DEFAULT_ORG_ID;
        IN_PROGRESS.set(Boolean.TRUE); // switch 内取组织树会发内部 SQL，先置重入保护
        try {
            switch (scope) {
                case ALL -> {
                    return;
                }
                case ORG_ONLY -> CURRENT_ORG_IDS.set(Set.of(orgId));
                case ORG_AND_CHILD -> {
                    // 组织树 provider 未装配或返回空时退化为仅本组织（查询期解析，避免装配期循环依赖）
                    OrgTreeProvider orgTreeProvider = orgTreeProviders.getIfAvailable();
                    Set<Long> ids = orgTreeProvider != null ? orgTreeProvider.getOrgAndChildIds(orgId) : null;
                    CURRENT_ORG_IDS.set(ids == null || ids.isEmpty() ? Set.of(orgId) : ids);
                }
                case DEPT_ONLY -> {
                    // 设计第 3 章：dept_id = 用户部门 AND org 条件（org 条件按 ORG_ONLY 档）
                    CURRENT_ORG_IDS.set(Set.of(orgId));
                    if (ctx.deptId() != null) {
                        CURRENT_DEPT_ID.set(ctx.deptId());
                        resolveDeptColumnTables(executor);
                    } else {
                        log.debug("用户{}未设置部门，DEPT_ONLY 退化为组织级（ORG_ONLY）过滤", ctx.userId());
                    }
                }
                case SELF -> CURRENT_SELF_USER_ID.set(ctx.userId());
            }
            if (CURRENT_ORG_IDS.get() == null && CURRENT_DEPT_ID.get() == null && CURRENT_SELF_USER_ID.get() == null) {
                return;
            }
            PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
            mpBs.sql(parserSingle(mpBs.sql(), ms.getId()));
        } finally {
            IN_PROGRESS.remove();
            CURRENT_ORG_IDS.remove();
            CURRENT_DEPT_ID.remove();
            CURRENT_SELF_USER_ID.remove();
        }
    }

    /**
     * SELECT 语句改写入口（JsqlParserSupport 模板方法，基类默认抛
     * UnsupportedOperationException，必须覆写）：委托 BaseMultiTable 的
     * 表遍历机制（PlainSelect/SetOperationList/JOIN/子查询统一处理），
     * 直接改写 AST，序列化由 processParser 统一完成。
     *
     * @param select 解析后的 SELECT AST
     * @param index  语句序号（多语句场景）
     * @param sql    原始 SQL
     * @param obj    透传参数（此处为 MappedStatement.getId，用于日志定位）
     */
    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        processSelectBody(select, obj == null ? null : obj.toString());
    }

    /**
     * 逐表构造数据权限条件（BaseMultiTableInnerInterceptor 回调，自动挂 ON/WHERE）。
     * <p>
     * DEPT_ONLY 组合语义：{@code org 条件 AND dept 条件}；dept 条件仅对探测到
     * 有 dept_id 列的表生效（无列的组织隔离表仅组织级过滤 + debug 提示）。
     *
     * @param table        SQL 中的表（含别名）
     * @param where        现有条件
     * @param whereSegment 语句标识（日志定位）
     * @return 数据权限条件表达式；无适用条件返回 null
     */
    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        String name = normalizeTableName(table.getName());
        if (name.isEmpty()) {
            return null;
        }
        Set<Long> orgIds = CURRENT_ORG_IDS.get();
        Long deptId = CURRENT_DEPT_ID.get();
        Long selfUserId = CURRENT_SELF_USER_ID.get();
        if (orgIds == null && deptId == null && selfUserId == null) {
            return null;
        }
        boolean orgScoped = orgIds != null && isOrgScopedTable(name);
        boolean deptScoped = deptId != null && deptColumnTables.contains(name);
        boolean selfScoped = selfUserId != null && isOrgScopedTable(name);

        Expression result = null;
        if (orgScoped) {
            // 带表限定名，JOIN 场景防歧义
            Column orgColumn = new Column(table, "org_id");
            if (orgIds.size() == 1) {
                result = new EqualsTo(orgColumn, new LongValue(orgIds.iterator().next()));
            } else {
                InExpression in = new InExpression();
                in.setLeftExpression(orgColumn);
                // jsqlparser 4.9：裸 ExpressionList 序列化为 "IN 1, 2, 3"（缺括号非法），必须用括号列表
                in.setRightExpression(new ParenthesedExpressionList<>(orgIds.stream().map(LongValue::new).toList()));
                result = in;
            }
        }
        if (deptScoped) {
            Expression deptCondition = new EqualsTo(new Column(table, "dept_id"), new LongValue(deptId));
            result = result == null ? deptCondition : new AndExpression(result, deptCondition);
        } else if (deptId != null && orgScoped) {
            log.debug("表[{}]无 dept_id 列，DEPT_ONLY 仅按组织过滤（whereSegment={}）", name, whereSegment);
        }
        if (selfScoped) {
            result = new EqualsTo(new Column(table, "create_by"), new LongValue(selfUserId));
        }
        return result;
    }

    /** 是否组织隔离表（注入对象） */
    private boolean isOrgScopedTable(String tableName) {
        if (tableName.isEmpty() || ORG_SCOPED_EXCLUDES.contains(tableName)) {
            return false;
        }
        if (ORG_SCOPED_EXACT.contains(tableName)) {
            return true;
        }
        return ORG_SCOPED_PREFIXES.stream().anyMatch(tableName::startsWith);
    }

    /** 表名归一化：去反引号/空白并转小写 */
    private static String normalizeTableName(String tableName) {
        return tableName == null ? "" : tableName.replace("`", "").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 探测当前库存在 dept_id 列的表集合（结果缓存 {@value #DEPT_TABLES_TTL_MILLIS}ms）。
     * <p>
     * 复用当前查询的连接（executor 事务连接），不额外占用连接池；
     * 探测失败（权限/连接异常）时保留旧集合并做退避，避免每条 SQL 重试拖垮请求，
     * 此时 DEPT_ONLY 自动退化为组织级过滤（保守方向）。
     */
    private void resolveDeptColumnTables(Executor executor) {
        long now = System.currentTimeMillis();
        if (now - deptTablesResolvedAt < DEPT_TABLES_TTL_MILLIS) {
            return;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - deptTablesResolvedAt < DEPT_TABLES_TTL_MILLIS) {
                return; // 双检：并发首批 DEPT_ONLY 查询只探测一次
            }
            try {
                Connection connection = executor.getTransaction().getConnection();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT TABLE_NAME FROM information_schema.COLUMNS "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'dept_id'");
                     ResultSet rs = ps.executeQuery()) {
                    Set<String> tables = new HashSet<>();
                    while (rs.next()) {
                        tables.add(rs.getString(1).toLowerCase(Locale.ROOT));
                    }
                    deptColumnTables = tables;
                    log.info("DEPT_ONLY dept_id 列表探测完成：{}（缓存{}分钟）", tables,
                            TimeUnit.MILLISECONDS.toMinutes(DEPT_TABLES_TTL_MILLIS));
                }
            } catch (SQLException e) {
                log.warn("探测 dept_id 列失败，DEPT_ONLY 本次退化为组织级过滤：{}", e.getMessage());
            }
            deptTablesResolvedAt = System.currentTimeMillis();
        }
    }

    /** 当前登录用户是否超管角色（绕过数据权限） */
    private boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (SUPER_ADMIN_ROLE.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
