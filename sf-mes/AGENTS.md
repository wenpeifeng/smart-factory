# MES 系统开发交接文档（新会话必读）

本仓库是一套自研 MES（制造执行系统），对标黑湖智造/鼎捷 MES，由前一会话启动开发，现移交新会话继续。**动手前先读本文档，再通读参照系。**

## 文档四件套（docs/，参照 sf-auth 结构）

系统概览（速览）→ 架构设计/MES与ERP协同设计（原理与契约）→ 数据字典（68 表，`script/sh/gen-data-dict.sh` 自动生成可刷新）→ 开发进度（会话史与踩坑清单）；模块细目在 docs/modules/。新会话先读 AGENTS.md 再读 docs/系统概览.md。

## 参照系（结构与规范的唯一模板）

`../sf-erp` —— 同一套技术栈的 ERP 系统，所有分层、命名、规范、文档格式照抄它：
- `erp/README.md`、`erp/docs/开发规范.md`（最重要）、`docs/架构设计.md`、`docs/modules/*.md`
- 通用组件已在 `mes-backend/mes-base/`（从 erp-framework 搬运，包名改为 com.mes）

## 技术栈

- 后端：Java 21 + Spring Boot 3.3 + MyBatis-Plus 3.5.7 + MySQL 8(mes_db, root/mes123456, 宿主端口 3307，3306 让给 erp-mysql) + Redis 7(宿主端口 6380)；后端服务端口 8081(8080 让给 erp-backend) + Flyway + JJWT + Hutool + MapStruct，包名 `com.mes`
- 前端：React 18 + TypeScript strict + Vite + AntD 5 + Tailwind + TanStack Query + Zustand + react-router 6 + Recharts（mes-web 已建成，复刻 erp-web 裁剪而来）
- 前后端同仓：`mes-backend/` + `mes-web/`，Flyway 脚本在 `script/sql/migration/`（仓库根为工作目录启动）

## 模块结构

```
mes-backend/
├── mes-base/          # mes-common + mes-framework（已完成搬运）
├── mes-platform/      # system(RBAC/租户/字典/日志) + workflow + notify（已完成）
├── mes-biz/           # basedata（已完成 CRUD）
│                      #   + plan/execution/quality/material/trace/equipment/kanban/
│                      #     integration/report（9 个为空骨架：仅 pom + package-info，无业务代码；
│                      #     其中 plan/execution/quality/material/trace 的表 DDL 已在 V2xx~V5xx 建好，
│                      #     P2/P3 从写 Java 开始，不需要再建表）
└── mes-bootstrap/     # MesApplication（@MapperScan("com.mes.**.mapper")）
```

Flyway 版本段：system V1x、workflow V9x、basedata V10x、plan V20x、execution V21x、quality V30x、material V40x、trace V50x、equipment V60x、kanban V70x、integration V80x、report V90x、demo-data V120x。已建表的模块：basedata/plan/execution/quality(含 SPC)/material/trace/workflow。

## 已确认的关键架构决策（不要推翻）

1. **与 ERP 边界**：ERP=计划层（MRP/生产订单/物料账/成本，见 ../sf-erp）；MES=执行层（排程/过站扫码报工/WIP/车间质检/追溯/领退料/OEE/看板）。分开部署、流程集成
2. **独立统一认证中心（../sf-auth，由专门会话独立建设，MES 不要开发认证中心本体）+ OIDC 接入**：身份、应用进门权、系统管理员标签（mes.admin）均由 auth-center 管理；主账号生命周期（入职/转岗/离职）只在 auth-center。MES 以 OIDC 客户端接入（授权码+PKCE，登录跳转 auth-center，回调换令牌，预留 /sso/callback；本地账号直登仅作认证中心不可用时降级）。MES 本地用户表为"已分配到本应用用户"的同步副本（调 auth-center 服务间 API 同步，软删除 active=false、对账式）；MES 管理端可对本应用用户做禁用/启用（调 auth-center 应用级启停接口，仅在职员工可用，离职用户不可操作）。**普通角色（报工员/质检员等）与菜单/按钮/数据权限完全在 MES 管理端内部维护**；mes.admin 标签→本地超管（通配全部菜单）。auth-center 端点契约以其仓库 docs 为准（client_id、/authorize、/token、JWKS、roles claim）；其未就绪前 MES 用契约 stub 开发联调
3. **integration 双向集成**（对 ERP）：生产订单+主数据(物料/BOM/工艺路线)下推 MES；MES 完工报工/领退料/质检结果回传 ERP；幂等+失败重试+对账补偿；ERP 侧未实现，先做契约+stub。用户/组织主数据同步来源为 auth-center（见第 2 条），不再来自 ERP
4. **单据规范**：Redis 单号生成器（前缀+yyyyMMdd+流水）、状态机（草稿→执行中→完工→关闭，支持红冲）、@RequirePerm/@OperLog/@Idempotent、多租户+数据权限五档、金额 DECIMAL(18,2)、数量 DECIMAL(18,4)、每表 8 公共列
5. **排程**只做工作中心甘特+手动拖拽，不做自动 APS
6. MES 前端首页=车间工作台（待办工单/扫码报工入口/安灯）；含 /m 移动端扫码报工门户

## 进度快照（2026-09-06 第六次会话结束，身份体系收敛 + ERP http 双向 + 语义修正）

**本会话完成**：修复后端编译（补 9 个 biz 骨架模块 + 父 pom dependencyManagement 换成 MES 模块清单 + notify 模块缺失/过期文件从 erp 同步修复 + ErpMessages.java→MesMessages.java 重命名）；建成 mes-web（复刻 erp-web 裁剪：登录/三门户/system+basedata+workflow 页面/工作台首页占位/移动 /m 占位）；docker-compose.yml + script/sh 三脚本；README + docs/架构设计.md + docs/MES与ERP协同设计.md；验收全过（mvn package、前端 build、Flyway 22 脚本）。

**环境要点（新会话必看）**：
- 默认 `java` 是 JDK 11，编译/启动必须用 `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`（start-all.sh 已内置）
- 端口：mes-mysql 3307 / mes-redis 6380 / 后端 8081 / 前端 5173；3306/6379/8080 被 erp 同名服务占用，且宿主上跑着一个 erp-backend 进程属正常现象，别误杀也别重用端口
- docker-compose 用镜像 `mysql:8` 实际是 8.4；首次初始化有秒级窗口 root@'%' 未就绪，若启动报 `Access denied for root@192.168.65.1`，执行 `docker exec mes-mysql mysql -uroot -pmes123456 -e "ALTER USER 'root'@'%' IDENTIFIED BY 'mes123456'; FLUSH PRIVILEGES;"` 后重启后端
- 启动：`./script/sh/start-all.sh`（全量）/ `--backend-only` / `--web-only`；停止：`./script/sh/stop-all.sh`（`--with-db` 连容器一起停）。会话结束时已停后端进程、保留 mes-mysql/mes-redis 容器与数据
- 登录账号 mes_admin/123456（套件统一账号规范：auth=admin、erp=erp_admin、mes=mes_admin，所有本地演示账号密码也统一为 123456）

**本会话（P2 执行核心）完成**：
- integration：ERP 六类接口契约+stub（ErpClient/AuthCenterClient/OidcService，mode=stub 默认）+ 回传推送日志 int_push_log（V801，幂等+5分钟重试+对账页 /integration/push-logs）+ 入站下推落地（订单→建单、物料/BOM/工艺路线→主数据，X-Integration-Token 鉴权）+ auth-center 用户对账同步 + OIDC 授权码+PKCE（/sso/callback 已入白名单）；docs/modules/integration.md
- plan：工单状态机+下达展开 BOM/工艺、派工/过站累计（WoStepService）、齐套分析、排程时间片；MesNoGenerator(4位流水) 供各模块复用
- execution：过站扫码报工（事务内记录+WIP+工序/工单累计+PASS 回传 ERP）、WIP 查询、返工/报废单
- material：领料/退料（审核=线边仓出入+回写工单已领+回传）、线边仓、批次SN 档案、标签打印留痕
- quality：检验模板/检验单（录值自动判定+提交汇总）/不合格处置，提交回传 ERP
- basedata 补齐：BOM/工艺路线/工作中心 entity+service+查询接口；TENANT_SHARED_TABLES 补制造主数据与 qc_template
- 前端：plan/execution/material/quality/integration 五组页面 + 菜单/路由/语言包；**V17 权限种子（含 P1 遗漏的 basedata 权限码）**
- 关键坑：①Lombok 1.18.28 与 JDK 21.0.12 不兼容已升 1.18.38（父 pom）；②角色权限 Redis 缓存 10 分钟（auth:perms:{uid}），权限变更后需删缓存；③plan_kit_check.result 无默认值，insert 需占位
- 端到端已验证：ERP 下推→建单→下达→派工→过站报工→WIP/工单累计→领料→齐套 FULL→质检提交→三类回传全 SUCCESS；验收（mvn package/npm build/start-all/Flyway V801+V17/登录）全过
- 详细设计：docs/modules/p2-core.md、docs/modules/integration.md

**本会话（P3 深化）完成**：
- trace：TraceEvents 事件契约（放 framework 中立层防依赖环）+ 领料/过站 AFTER_COMMIT 采集 trace_link + 正反向追溯接口 + EBR 电子批记录（幂等快照）；docs/modules/p3-deepen.md
- SPC：规则 CRUD + 检验提交自动采样 + R1/R2/R3/R5(+R4) 判异 + Recharts 控制图页
- equipment（V601 新建表）：台账/点检（NG 自动置维修）/保养维修（完成自动恢复）/OEE 实时聚合接口与页
- kanban（V701 新建表）：安灯 OPEN→RESPOND→CLOSED + 30 秒轮询页
- 排程甘特页（/plan/schedules 数据可视化）；移动端 /m/scan 报工页落地（替换 P1 骨架）
- P2 遗留清理：齐套可用量改 material 适配器实现 plan SPI、Bom/Routing 出参 VO 化
- V18 权限种子（trace/spc/equipment/kanban）
- 修复：SPC BigDecimal 除法需带 scale；两处 Map.of().get(null) NPE（Andon/Device 列表 workcenterId 为空时）
- 端到端验证：关键件SN双向追溯、EBR 幂等、SPC 超限样本命中 R1、点检 NG 设备转维修、安灯闭环、OEE 计算、/m 报工接口链路

**本会话（P4 呈现层）完成**：
- report 模块（V19 权限）：StatsMapper 只读聚合 + /report/screen（大屏）/cockpit（驾驶舱）+ 工单达成/报工明细/质量统计（JSON+CSV）
- 车间电子看板大屏 /kanban/screen（深色电视墙，30 秒轮询：安灯/产量/进度榜/WIP/质量）
- 管理驾驶舱 /cockpit（指标卡+趋势+状态分布+合格率，60 秒轮询）
- 报表中心三页（/report/*，CSV 导出）
- demo-data（V1201）：演示角色 demo_operator/demo_qc/demo_wh + 演示账号 operator/qc/wh（密码 123456），已验证权限边界（operator 可报工、访问驾驶舱 403）
- 修复：V19 驾驶舱菜单误建为目录（type=1 权限码不生效，改 type=2）
- 数据库答疑：三系统三库已就绪（mes_db@mes-mysql:3307 / erp_db@erp-mysql:3306 / auth_db@auth-mysql:13306），MES 自有完整 RBAC 表（sys_user/role/menu/role_menu/user_role，104+ 菜单），auth-center 只管身份与进门权；docs/modules/p4-presentation.md
- 端到端验收全过：mvn package/npm build/start-all/Flyway V19+V1201/登录/大屏与驾驶舱聚合数据/演示账号权限边界

**本会话（sf-auth 真实对接 + 集成测试）完成**：
- AuthCenterClient http 实现：service token（client_credentials）Redis 缓存 + 用户同步适配真实契约（employeeNo 匹配键/mes.admin→本地超管映射/active=false 软删除）+ 应用级启停；mode 已切 http（yml 指向 8082）
- OIDC http：authorize 跳 auth-web 统一登录页(5174) + /integration/oidc/login（白名单）授权码+PKCE 换令牌 + JWKS(RS256) 验签（aud 强校验；auth 当前令牌无 iss claim，缺失跳过）+ AuthService.buildSession 桥接 MES 会话
- 前端：登录页"统一认证登录(SSO)"按钮 + /sso/callback 公开回调页
- 集成测试：script/sh/integration-test.sh（19 项断言：本地登录/权限边界/用户同步/SSO 全链路含重放拒绝/ERP 下推幂等/下达派工过站回传/质检/安灯，全过）+ PkceUtilTest（3 用例，RFC 7636 官方向量）
- 联调坑：①auth 种子把 mes-web 回调注册成了 8081，已改 5173（auth_db.app_client）；②已应用迁移脚本改内容会 Flyway checksum 报错（dev 手工 UPDATE flyway_schema_history）；③spring-boot-starter-test 父 pom dependencyManagement 需显式版本
- 默认登录方式：SSO（admin/123456 走 5174 统一登录）或本地降级（mes_admin/123456，套件统一账号规范；演示账号 operator/qc/wh=123456）

**本会话（身份体系收敛 + ERP 双向 http + 建模修正）完成**：
- **用户体系入口收敛（决策 2 落地）**：MES 端用户新增/修改/删除/重置密码/本地启停接口**已物理删除**（controller/service/api/ts 全链路移除，HTTP 404/405）；SysUserServiceImpl 重写为只读+角色分配；用户管理页改为「同步认证中心用户 + 应用级启停（调中心，仅在职可用）+ 分配角色」；V20 删除增删改密按钮权限
- **三层同步**：手动同步按钮（用户页）+ **JIT 登录建档**（SSO 回调本地无此工号时直接用验签过的 claims 建档，不依赖定时与列表接口）+ 每小时全量对账（AuthUserSyncJob，含孤儿禁用：中心已回收的同步来源用户置 status=0）。不用 updatedSince 增量——中心离职/停用不触碰分配表，增量必漏
- **身份键语义修正（V21）**：sys_user 增加独立 employee_no 唯一列（不可变身份键，同步/JIT/SSO 匹配键），username 只存登录名（来自中心 username，默认=工号，可分道扬镳）；存量同步用户已回填；孤儿判定/应用级启停均走工号
- **ERP 双向 http（mode=http）**：ErpClientHttpImpl（回传四接口直推 ERP 8080，X-Integration-Token，失败走推送日志重试）+ 工单进度镜像（plan 状态流转发 IntegrationEvents.WoStatusChanged → integration AFTER_COMMIT 异步推 /integration/mes/order-status）；实测对真实 ERP 全 SUCCESS、mrp_work_order 数量实收
- ERP 会话跨仓改动已核实无冲突（SecurityConfig 白名单 /integration/erp/**）；集成测试套件 19/19 回归通过
- 联调注意：ERP 入站令牌=出站令牌=erp-integration-token（mes.integration.erp.outbound-token）；测试脚本 ./script/sh/integration-test.sh

**后续可选方向**：甘特拖拽编辑、SPC 预警通知接入 notify、报表导出改签名 URL、单据对账补偿任务、性能（聚合 SQL 加索引/缓存）。

## 剩余工作（分期，依次完成）

- ~~**P0 认证中心**~~：auth-center 已独立为 `../sf-auth` 仓库并由专门会话建设，MES 不再负责开发；MES 侧只在 integration 中实现 OIDC 客户端对接（按第 2 条，契约未就绪时用 stub）
- **P1 收尾（已完成 2026-09-06）**：mes-web 前端骨架（复刻 erp-web：request.ts/PageTable/三门户/工作台首页/移动扫码占位）、docker-compose.yml 与 script/sh 三脚本、README 与 docs 初版（含《MES 与 ERP 协同设计》）、编译/启动/Flyway(22 脚本)/登录自检全部通过。mes-biz 其余 9 个模块为骨架目录（仅 pom）。
- **P2 执行核心**：integration 契约与 stub、plan 工单接收/齐套分析、execution 派工/过站扫码报工/WIP/完工回传、material 领退料+批次SN+标签打印、quality IQC/IPQC/OQC
- **P3 深化**：trace 正反向追溯/电子批记录、SPC、equipment 台账/点检保养/OEE、kanban 安灯、排程甘特看板
- **P4 呈现层**：车间电子看板、管理驾驶舱、报表中心、移动端报工、demo-data 演示剧本与演示账号

## 质量要求

- 遵循 ../sf-erp/docs/开发规范.md 的全部约定（分层/REST/命名/注释/Git）
- 每完成一个业务模块：调用 code-review 技能评审并修复 P0/P1，对冗余实现调用 simplify 技能
- 验收：后端 `mvn package` 通过、前端 build 通过、docker-compose up 后 Flyway 全过、可登录
- 环境：brew 在 /opt/homebrew/bin；GitHub 直连不稳定，Maven/npm 用阿里云镜像；JDK/Node/Maven 缺失先安装
