# Smart Factory 智慧工厂数字化套件

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB.svg)](https://react.dev/)

Smart Factory 是一套面向中小制造企业的开源数字化解决方案，将统一身份认证、ERP 经营管理与 MES 车间执行整合在同一个可复现的工程中。项目适合用于制造业数字化方案演示、业务学习、二次开发和技术交流。

套件由三个**独立部署、独立数据库、统一身份、流程集成**的系统组成：

```
                    ┌─────────────────────────────┐
                    │   sf-auth 统一认证中心        │  ← 平台层：身份与应用授权
                    │   主账号生命周期 / OIDC / SSO  │
                    └──────────┬────────┬─────────┘
                               │        │
              ┌────────────────┘        └───────────────┐
              ▼                                         ▼
┌─────────────────────────┐              ┌─────────────────────────┐
│  sf-erp  ERP 经营管理层  │ ──生产订单──▶ │  sf-mes  MES 车间执行层  │
│  采购/销售/库存/生产/财务  │ ◀─完工回传──  │  工单/报工/质检/追溯/OEE  │
└─────────────────────────┘              └─────────────────────────┘
```

## 项目亮点

- **统一身份入口**：员工生命周期、应用分配和系统管理员标签统一由认证中心管理，ERP/MES 只维护本系统角色与数据权限。
- **完整制造业链路**：覆盖采购、销售、库存、生产、财务、质检、设备、追溯、报工和 OEE 等核心场景。
- **ERP 与 MES 协同**：支持主数据、生产订单下推，以及完工、领退料和质检结果回传，并提供重试与对账机制。
- **开箱即用的演示数据**：初始化数据跟随 Flyway 迁移交付，首次启动即可获得一致的账号和业务数据。
- **前后端分离**：Java 21 + Spring Boot 3.3，React 18 + TypeScript + Ant Design，适合作为企业级项目参考。
- **中文文档与界面**：根目录和各子系统均提供中文说明，便于理解系统边界、账号体系和启动流程。

> 当前项目以学习、演示和二次开发为主要目标。生产部署前请完成密钥替换、网络隔离、权限复核、数据备份和安全加固。

## 三系统职责与边界

| 系统 | 层级 | 管什么 | 数据库/端口 |
|---|---|---|---|
| sf-erp | 经营管理层 | 计划与账：MRP、采购到付款、销售到收款、库存成本、总账财务 | erp_db / MySQL 3306，后端 8080 |
| sf-mes | 车间执行层 | 过程与执行：工单排程、过站扫码报工、WIP、SPC 质检、批次/SN 追溯、设备 OEE、电子看板 | mes_db / MySQL 3307，Redis 6380，后端 8081 |
| sf-auth | 平台层 | 身份与应用授权：主账号生命周期（入职/转岗/离职）、应用注册与分配、系统管理员标签、OIDC 授权码 SSO、统一审计 | auth_db / MySQL 13306，Redis 16379，后端 8082 |

**身份打通（物业门禁模型）**：账号与"能进哪个系统"只在 sf-auth 管理；各系统内部的角色→菜单/按钮/数据权限由各系统管理端自管；系统管理员标签由认证中心分配。登录一次，处处通行（OIDC 授权码模式 + PKCE）。**用户体系关系与业务系统接入规范见 [docs/统一账号体系说明.md](docs/统一账号体系说明.md)（套件级权威文档，新系统接入必读）。**

**业务打通（ERP↔MES 集成）**：ERP 生产订单与主数据（物料/BOM/工艺路线）下推 MES；MES 完工报工、领退料、质检结果回传 ERP 生成入库与成本数据；幂等 + 失败重试 + 对账补偿。详见 `sf-mes/docs`《MES 与 ERP 协同设计》。

## 目录结构

```
smart-factory/
├── README.md            # 本文件（套件总览）
├── script/
│   ├── start-all.sh     # 一键启动三系统（重启语义：先停后启 + 健康检查汇总）
│   └── stop-all.sh      # 一键停止
├── docs/
│   └── 统一账号体系说明.md  # 套件级：账号体系关系与新系统接入规范
├── sf-erp/              # ERP（见其 README）
├── sf-mes/              # MES（见其 README）
└── sf-auth/             # 统一认证中心（见其 README）
```

## 技术栈（三系统统一）

- 后端：Java 21 · Spring Boot 3.3 · MyBatis-Plus · MySQL 8 · Redis 7 · Flyway · JWT/OIDC
- 前端：React 18 · TypeScript · Vite · Ant Design 5 · TanStack Query · Zustand
- 部署：Docker Compose（数据库/缓存）+ 原生进程（应用），后续可容器化整体交付

## 启动方式

### 一键启动

```bash
cd smart-factory
./script/start-all.sh     # 重启语义：先自动停止旧进程，再依次启动 sf-auth → sf-erp → sf-mes
./script/stop-all.sh      # 一键停止（数据库容器保留；--with-db 连容器一起停）
```

### 一键停止
```bash
./script/stop-all.sh      # 一键停止（数据库容器保留；--with-db 连容器一起停）
```

### 独立启动（三系统互不依赖，可单独起停）

```bash
cd sf-erp  && ./script/sh/start-all.sh   # 含基础设施（容器运行中会自动跳过）
cd sf-mes  && ./script/sh/start-all.sh
cd sf-auth && ./script/sh/start-all.sh
```

> 顶层 `script/start-all.sh` 会在 macOS 上自动启动已安装的 Docker Desktop，并等待 daemon 就绪后再停止和重启应用。基础设施容器由各项目 `script/sh/env-init.sh`（docker run）管理，运行中/已停止/不存在三种状态都会自动处理；不要手动执行 `docker compose up`，会与已存在的同名容器冲突。docker-compose.yml 仅作全新环境的兜底创建方式。

> **首次启动数据保证**：所有演示数据都由版本化 Flyway 迁移随源码交付。人员、应用分配和系统管理员标签只在 `sf-auth/script/sql/migration` 定义；ERP/MES 启动时从认证中心自动重建本地授权副本，顶层启动脚本返回前还会强制收敛一次。ERP 业务演示数据由 `V1201__demo_data.sql` 初始化，MES 全链路演示数据由 `V1203__mes_business_demo_data.sql` 和 `V1204__demo_data_consistency_fix.sql` 初始化。因此其他人 clone GitHub 项目并对空数据库首次启动时，看到的是同一套可复现数据，不依赖某台电脑的本地数据库副本。

> **运行期实时一致性**：在认证中心、ERP 管理端或 MES 管理端修改人员档案、启停、应用分配或管理员标签后，认证中心会在数据库事务提交后立即通知相关子系统做幂等对账。若某子系统暂时离线，其每分钟全量对账会自动补齐，认证中心始终是人员唯一事实源。

> 说明：三系统独立部署。sf-auth 不可用时，ERP/MES 各自保留本地账号直登降级；ERP 与 MES 未互通时，MES 以集成契约 stub 自行运行演示。

### 推荐体验顺序：
1. 进入认证中心创建员工，分配 ERP/MES 应用访问权。
2. 从 ERP/MES 登录页走统一认证。
3. 在 ERP 建物料、BOM、工艺路线和生产订单。
4. 将订单下推 MES。
5. 在 MES 完成下达、派工、过站报工、领料和质检。
6. 回 ERP 检查生产进度、入库及成本回传。

## 访问入口与演示账号

> 页面一律从**前端端口**访问（5172/5173/5174）；8080/8081/8082 是后端 API 网关（路径前缀 `/api`），浏览器直接打开会 404。

| 系统 | 页面访问地址（前端） | 后端 API | 演示账号 | 说明 |
|---|---|---|---|---|
| sf-auth 管理端 | http://localhost:5174 | http://localhost:8082/api | `admin / 123456`（只读种子超管，仅演示） | 建人、管应用分配；也可统一登录 ERP/MES |
| sf-erp 业务门户 | http://localhost:5172 | http://localhost:8080/api | `E3003 / 123456`（ERP 管理员） | 正式入口选“统一认证登录” |
| sf-mes 车间工作台 | http://localhost:5173 | http://localhost:8081/api | `E3002 / 123456`（MES 管理员） | 正式入口选“统一认证登录” |

> `E3001` ~ `E3006` 是只读种子账号；`E3101` ~ `E3105` 是可维护的入职员工账号，密码均为 `123456`。应用分配矩阵见 [sf-auth/README.md](sf-auth/README.md)。
> `erp_admin / 123456` 与 `mes_admin / 123456` 仅为认证中心故障时的本地应急账号，不参与正常的入职、应用分配与离职回收链路。

## 端口与资源占用总览

| 资源 | sf-erp | sf-mes | sf-auth |
|---|---|---|---|
| MySQL | 3306 | 3307 | 13306 |
| Redis | 6379 | 6380 | 16379 |
| 后端 API | 8080 | 8081 | 8082 |
| 前端页面 | 5172 | 5173 | 5174 |

## 开发规范

三系统共用同一套开发规范（分层/REST/命名/Flyway 版本段/前端组件规范），规范母本：`sf-erp/docs/开发规范.md`。新增系统遵循同样格式。

## 参与贡献

欢迎通过 Issue 提交缺陷、需求或使用反馈，也欢迎提交 Pull Request。贡献代码前建议先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，涉及账号、权限、数据库迁移或跨系统契约的改动，请同时补充对应文档和验证步骤。

## 开源与安全说明

- 本项目基于 [MIT License](LICENSE) 开源，可用于学习、修改和二次开发。
- 仓库中的 `123456`、`demo-secret` 及各类 `*-token` 均为本地演示默认值，不是线上凭据。
- 公网或生产环境必须通过环境变量覆盖数据库密码、Redis 密码、OIDC 客户端密钥、JWT/同步令牌和 ERP-MES 集成令牌。
- 如发现安全问题，请不要公开披露利用细节，按 [SECURITY.md](SECURITY.md) 提交私密安全报告。

## Roadmap

- [x] sf-erp：采购/销售/库存/生产/财务/成本/质检/资产/HR/预算等 20 模块
- [~] sf-mes：P1 骨架与主数据完成，P2 执行核心进行中
- [~] sf-auth：设计定稿，开发中
- [ ] 套件级：统一 docker-compose（一键起基础设施）、Nginx 统一网关与域名、生产容器化
