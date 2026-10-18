# ERP 管理系统

对标金蝶 K3 / 用友 U8 的**集团级开源 ERP** —— 供应链 + 财务 + 生产制造 + 质量 + 成本 + 预算一体化，多租户 / 多组织 / 多账套架构。

> 演示环境已内置「苏州精工机械有限公司」完整业务剧本（六个月连贯数据），登录即可体验全部模块。

## 快速开始

```bash
# 一键启动（环境检查 → 数据库容器 → 后端编译启动 → 前端 dev server）
./script/sh/start-all.sh

#    前端   http://localhost:5173
#    后端   http://localhost:8080/api        接口文档 /api/swagger-ui.html
#    移动端 http://localhost:5173/m/scan     仓库扫码作业

# 停止：./script/sh/stop-all.sh   （连库停：--with-db）
```

**演示账号**：平台超管 `erp_admin / 123456`；以下角色账号密码也统一为 `123456`：

| 账号 | 角色 | 体验点 |
|---|---|---|
| `erp_admin` / `123456` | 平台超管 | 全部功能 + 管理门户（/admin）+ 三组织切换 |
| `zhanggm` | 总经理 张国民 | 经营驾驶舱、2 条采购审批待办 |
| `wangcg` / `liuxs` / `zhouchs` / `malin` / `hucang` / `qizj` | 采购/销售/车间/会计/仓管/质检 | 各岗位视角（按权限码过滤菜单与按钮） |

## 技术栈

**后端**：Java 21 · Spring Boot 3.3 · MyBatis-Plus 3.5（多租户插件/数据权限拦截器/逻辑删除/审计填充）· MySQL 8 · Redis 7 · Spring Security + JWT · Flyway（35 个版本化迁移）· springdoc-openapi · MapStruct · logstash-logback（JSON 结构化日志 + traceId）

**前端**：React 18 · TypeScript 5.5(strict) · Vite 5 · Ant Design 5 · Tailwind CSS · Framer Motion · Recharts · TanStack Query · Zustand · react-i18next（中/英）

**测试**：JUnit 5 + Testcontainers（MySQL 8 真库集成测试，13 用例守核心不变量）

## 模块架构（20 个 Maven 模块，三层前缀命名 + 分组聚合）

> 采用 Maven **聚合分组**结构（聚合≠继承：分组壳只做目录组织，子模块 parent 直接继承根 POM，依赖管理单一来源；artifactId 不变，模块间依赖不受目录影响）。

```
erp-backend (根 POM：依赖管理 + 总聚合)
│
├── erp-base/                聚合组：基础技术层
│   ├── erp-common             纯 Java 公共库（Result/异常体系/错误码/i18n key）
│   └── erp-framework          技术框架（Security/MP插件/幂等/限流/数据权限/分页/traceId）
│
├── erp-platform/            聚合组：平台能力层
│   ├── erp-platform-system    SSO认证·RBAC·租户·组织·字典·三类日志
│   └── erp-platform-workflow  审批流引擎（条件路由/或签/待办/消息/预警）
│
├── erp-biz/                 聚合组：业务域层（15 个）
│   ├── erp-biz-basedata       基础资料（物料/客商/仓库/库位/单位/结算）
│   ├── erp-biz-purchase       采购（订单→入库→退货→对账）
│   ├── erp-biz-sales          销售（订单→出库→退货·信用管控·毛利统计）
│   ├── erp-biz-inventory      库存（StockService 唯一入口·批次FIFO·正反溯·盘点·移动端API）
│   ├── erp-biz-qc             质检（IQC/IPQC/FQC·不合格品·合格率）
│   ├── erp-biz-finance        财务（应收应付核销·凭证·多账套·试算平衡）
│   ├── erp-biz-cost           成本（移动加权·月结自动凭证·工单料工费）
│   ├── erp-biz-mrp            生产（BOM·MRP·工艺路线·派工报工·能力CRP·委外）
│   ├── erp-biz-asset          固定资产（折旧自动凭证·处置）
│   ├── erp-biz-hr             人力（档案·考勤·计件工资联动报工）
│   ├── erp-biz-equipment      设备（台账·保养·维修备件出库）
│   ├── erp-biz-invoice        发票（销项/进项·红冲·税负）
│   ├── erp-biz-budget         预算（矩阵编制·WARN/BLOCK控制·执行分析）
│   ├── erp-biz-org            组织（多工厂·组织间调拨结算价）
│   └── erp-biz-report         报表（驾驶舱·多维报表·CSV·单据打印中心）
│
└── erp-bootstrap/           启动聚合器（唯一可执行 jar + 全局配置 + i18n 资源）
```

## 模块依赖关系

```
                    ┌─────────────── erp-bootstrap ───────────────┐
                    │        （聚合全部模块，无业务代码）            │
                    └──────────────────────┬──────────────────────┘
  ┌──────────┬──────────┬──────────┬──────┴─────┬──────────┬──────────┐
  │ purchase │  sales   │   mrp    │  qc/budget │  report  │ asset/hr │
  └────┬─────┴────┬─────┴────┬─────┴─────┬──────┴────┬─────┴────┬─────┘
       │          │          │           │           │          │
       ▼          ▼          ▼           ▼           ▼          ▼
  ┌────────────────────────────────────────────────────────────────┐
  │  跨模块 Service 直调（编译期单向依赖，禁循环）：                    │
  │  StockService(库存唯一入口) CostService(计价)                    │
  │  BudgetControlService(预算) WorkflowService(审批)               │
  │  QC pending(质检卡点)  OrgContextHolder(租户/组织上下文)          │
  └────────────────────────────────────────────────────────────────┘
       │
       ▼
  erp-biz-inventory ──► erp-biz-basedata ──► erp-platform-system ──► erp-framework ──► erp-common
  （业务域层）              （共享主数据）        （认证/权限/租户）      （技术框架）     （纯工具）
```

依赖方向铁律：只能向下/向左，业务模块间禁止横向 import 实体（跨模块一律走 Service 接口或约定表只读 JOIN）。

## 关键架构机制

| 机制 | 说明 | 文档 |
|---|---|---|
| 多租户 | 106 张表全量 tenant_id，MP 插件自动注入查询条件/写入填充 | docs/集团组织与数据权限设计.md |
| 数据权限 | 五档数据范围（ALL/组织及下级/仅本组织/…），拦截器零侵入注入 org 条件 | 同上 |
| 公共字段 | BaseEntity：id/tenant/org/create_by/create_time/update_by/update_time/deleted，切面自动填充，@TableLogic 自动过滤 | docs/开发规范.md |
| 接口鉴权 | @RequirePerm 权限码注解（253 码），前端 HasPerm 按钮显隐同源 | docs/modules/system.md |
| 三类日志 | 登录日志/操作日志(@OperLog)/业务链路 log，JSON+traceId 全链路 | docs/日志规范.md |
| 幂等限流 | @Idempotent 防重复提交、@RateLimit 滑动窗口限流 | erp-framework |
| 分页规范 | PageQuery 基类 + 排序字段白名单（防 SQL 注入）+ 前端 PageTable | docs/开发规范.md |
| 多账套 | 财务按 X-Book-Id 切换，科目/凭证/核销完全隔离 | docs/modules/finance.md |
| 国际化 | 后端 MessageSource + 前端 react-i18next，Accept-Language 全链路；**业务异常消息全量 key 化（788 处，768 key）** | docs/modules/i18n.md |
| 通用红冲 | 已审单据一键红冲（6 类单据：采购/销售出入库、退货、收付款），负数反向单+库存/往来/凭证反向，biz_reverse_log 防重 | docs/modules/reverse-docchain.md |
| 单据联查 | 报价→订单→出入库→发票→应收应付→收付款→凭证 全链上下游追溯（BFS 深度 8） | 同上 |
| 倒冲领料 | 工单完工按 BOM×数量×(1+损耗) 自动倒冲出库，主料不足按优先级用替代料 | docs/modules/mrp.md §11 |
| 供应商准入/评级 | 准入审批（workflow 双通道）+黑名单下单拦截；月/季度交易评分（准时/合格/退货加权） | docs/modules/supplier-admit-rating.md |
| 统一认证 SSO | 接入集团认证中心（OIDC 授权码+PKCE、JWKS 验签、SCIM 用户同步、erp.admin 标签），本地账密降级保留 | docs/modules/sso-authcenter.md |
| ERP↔MES 集成 | 六类接口（主数据/生产订单下推，报工/领退料/质检/进度回传），outbox 指数退避重试+幂等消息日志+集成监控页 | docs/modules/mes-integration.md |
| 三门户 | 业务门户 / 管理门户(/admin) / 移动门户(/m) | docs/modules/portal.md |

> **文档导航（四层：速览→原理→对接→交接）见 [docs/README.md](docs/README.md)**；一页速览（能力总览+140 张表目录）见 [docs/系统概览.md](docs/系统概览.md)。

## 环境要求与数据库

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21+ | `brew install openjdk@21`（JAVA_HOME 已写入 ~/.zshrc） |
| Maven | 3.9+ | ~/tools/maven（阿里云镜像已配 ~/.m2/settings.xml） |
| Node | 18+ | 推荐 22 |
| Docker | 近期版 | MySQL 8 / Redis 7 容器 |

**数据库连接（DataGrip/Navicat）**：`localhost:3306` root/`erp123456` 库 `erp_db`；Redis `localhost:6379`（密码 erp123456）。
建表/改表只允许新增 `script/sql/migration/V<版本>__描述.sql`（Flyway 自动执行，历史脚本不可改）。

## 集成测试

```bash
cd erp-backend && mvn -pl erp-bootstrap -am test -Dgroups=it
# 13 用例：库存并发防负 / 移动加权计价 / 凭证借贷平衡 / 信用管控
# Testcontainers 独立 MySQL，不碰主库
```

## 维护规则（新增/修改模块必读）

1. **新增模块**：目录用 `erp-biz-<域>`（平台能力用 `erp-platform-*`），父 POM `modules`+`dependencyManagement`、`erp-bootstrap` 依赖、`@MapperScan` 通配自动生效；**同步更新本 README 模块清单与依赖图**
2. **新增页面**：`router/modules/<模块>.tsx` 路由片段 + MainLayout 菜单（主线程合并）+ `sys_menu` 权限码（V 脚本）+ Controller 挂 `@RequirePerm`
3. **统一规范**：列表页必须用 `PageTable/PageHeader/StatusTag`，写操作必须 `useSubmitting` + 后端 `@Idempotent`，动效仅限路由转场与图表入场（详见 docs/前端交互统一规范.md）
4. SQL 版本段分配见 docs/开发规范.md §4（system V1x / basedata V10x / purchase V20x …）
