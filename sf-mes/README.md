# MES 制造执行系统

对标黑湖智造 / 鼎捷 MES 的自研制造执行系统 —— 排产派工 · 过站扫码报工 · WIP 跟踪 · 车间质检 · 批次/序号追溯 · 领退料 · 设备 OEE · 车间电子看板，多租户 / 数据权限分治架构。

与同仓技术栈的 **ERP 系统**（`../sf-erp`，计划层）流程集成：ERP 下推生产订单与主数据，MES 执行后回传报工/领退料/质检结果。边界与集成契约详见 [docs/MES与ERP协同设计.md](docs/MES与ERP协同设计.md)。

> 当前进度：**P1 收尾完成**（后端平台+基础数据可用、前端骨架就绪、一键启动）。P2 执行核心（工单/派工/过站报工/领退料/质检）按分期计划推进，详见下文路线图。

## 快速开始

```bash
# 一键启动（环境检查 → MySQL/Redis 容器 → 后端编译启动 → 前端 dev server）
./script/sh/start-all.sh

#    前端   http://localhost:5173
#    后端   http://localhost:8081/api        接口文档 /api/swagger-ui.html
#    移动端 http://localhost:5173/m/login    车间扫码报工门户（P1 骨架）

# 停止：./script/sh/stop-all.sh   （连库停：--with-db）
# 仅数据库容器：docker compose up -d
```

**默认账号**：`mes_admin / 123456`（平台超管；P4 演示剧本将补充车间各岗位账号）。

环境要求：JDK 21、Maven、Node 18+、Docker（MySQL 8 容器 `mes-mysql`，宿主端口 **3307**（3306 让给 erp-mysql），库 `mes_db`，root/mes123456；Redis 7 容器 `mes-redis`，宿主端口 **6380**，密码 mes123456；后端端口 **8081**（8080 让给 erp-backend），前端 dev 5173 代理 `/api` → 8081）。

## 技术栈

**后端**：Java 21 · Spring Boot 3.3 · MyBatis-Plus 3.5（多租户插件/数据权限拦截器/逻辑删除/审计填充）· MySQL 8 · Redis 7（单号生成/幂等/限流）· Spring Security + JWT（与 ERP 共享密钥 SSO）· Flyway · springdoc-openapi · MapStruct · Hutool

**前端**：React 18 · TypeScript 5.5(strict) · Vite 5 · Ant Design 5 · Tailwind CSS · Framer Motion · Recharts · TanStack Query · Zustand · react-i18next（中/英）

开发规范遵循 `../sf-erp/docs/开发规范.md`（分层/REST/命名/注释/Git 全部照抄）。

## 模块架构

```
mes-backend/
├── mes-base/                      # mes-common(纯 Java 工具/统一响应) + mes-framework(技术栈横切)
├── mes-platform/                  # system(RBAC/租户/字典/日志) + workflow(审批流) + notify(通知中心)
├── mes-biz/                       # 业务域聚合组
│   ├── mes-biz-basedata/          # ✅ 基础资料：物料/客户/供应商/仓库/计量单位/结算方式
│   ├── mes-biz-plan/              # 🔲 P2 计划：工单接收/齐套分析/工作中心甘特排程
│   ├── mes-biz-execution/         # 🔲 P2 执行：派工/过站扫码报工/WIP
│   ├── mes-biz-quality/           # 🔲 P2 质量：IQC/IPQC/OQC（P3 SPC）
│   ├── mes-biz-material/          # 🔲 P2 物料：领退料/批次 SN/标签打印
│   ├── mes-biz-trace/             # 🔲 P3 追溯：正反向追溯/电子批记录
│   ├── mes-biz-equipment/         # 🔲 P3 设备：台账/点检保养/OEE
│   ├── mes-biz-kanban/            # 🔲 P3 看板：车间电子看板/安灯
│   ├── mes-biz-integration/       # 🔲 P2 集成：ERP 双向集成/用户同步/SSO
│   └── mes-biz-report/            # 🔲 P4 报表中心
└── mes-bootstrap/                 # 启动聚合（@MapperScan com.mes.**.mapper）

mes-web/                           # React 前端（三门户：业务 / /admin 管理 / /m 移动扫码）
script/sql/migration/              # Flyway 版本化脚本（以仓库根为工作目录启动）
```

Flyway 版本段：system V1x · workflow V9x · basedata V10x · plan V20x · execution V21x · quality V30x · material V40x · trace V50x · equipment V60x · kanban V70x · integration V80x · report V90x · demo-data V120x。

## 关键架构决策（不要推翻）

1. **与 ERP 边界**：ERP=计划层（MRP/生产订单/物料账/成本），MES=执行层（排程/过站报工/WIP/车间质检/追溯/领退料/OEE/看板）。分开部署、流程集成。
2. **统一认证中心（../sf-auth，独立仓库）+ OIDC 接入**：身份/应用进门权/mes.admin 标签在 auth-center；MES 以 OIDC 客户端接入（授权码+PKCE，预留 /sso/callback），本地账号直登仅作降级；用户/组织为 auth-center 同步副本；普通角色与菜单/按钮/数据权限在 MES 管理端内部维护。
3. **integration 双向集成**（对 ERP）：生产订单+主数据下推 MES；完工报工/领退料/质检结果回传 ERP；幂等+失败重试+对账补偿。用户/组织主数据同步来源为 auth-center（见第 2 条），不再来自 ERP。
4. **单据规范**：Redis 单号生成器（前缀+yyyyMMdd+流水）、状态机（草稿→执行中→完工→关闭，支持红冲）、@RequirePerm/@OperLog/@Idempotent、多租户+数据权限五档、金额 DECIMAL(18,2)、数量 DECIMAL(18,4)、每表 8 公共列。
5. **排程**只做工作中心甘特+手动拖拽，不做自动 APS。
6. **前端首页=车间工作台**（待办工单/扫码报工入口/安灯），含 `/m` 移动端扫码报工门户。

## 文档

- [docs/MES与ERP协同设计.md](docs/MES与ERP协同设计.md) —— 边界、六类集成接口、幂等与对账、SSO 选型
- [docs/架构设计.md](docs/架构设计.md) —— 分层/模块/横切机制
- `../sf-erp/docs/开发规范.md` —— 唯一编码规范模板
