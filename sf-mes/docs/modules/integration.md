# integration 集成模块设计（P2 v1）

> 模块：`mes-backend/mes-biz-integration`（包 `com.mes.integration`）。
> 职责：对 ERP 双向集成（3 下推 + 3 回传，幂等/重试/对账）、auth-center 用户同步、OIDC 客户端接入。
> 两端未就绪前一律 `mode=stub`（契约桩，仅记日志返回成功）。

## 1. 对 ERP 六类集成接口契约

### 1.1 入站（ERP -> MES，均为 POST /integration/erp/**，按单号幂等）
| # | 接口 | DTO | 落地 |
|---|------|-----|------|
| 1 | 生产订单下推 `POST /integration/erp/orders` | `ErpProductionOrderDTO`（orderNo 幂等） | 调 plan 模块 `WorkorderService.receiveFromErp` 建 plan_workorder |
| 2 | 物料主数据下推 `POST /integration/erp/materials` | `ErpMaterialDTO`（code 幂等） | upsert base_material |
| 3 | BOM/工艺路线下推 `POST /integration/erp/boms`、`/routings` | `ErpBomDTO` / `ErpRoutingDTO` | upsert base_bom(base_bom_item)/base_routing(_step) |

入站接口由 ERP 侧携带请求头 `X-Integration-Token`（配置 `mes.integration.erp.inbound-token`）调用。

### 1.2 出站（MES -> ERP，`ErpClient` 接口 + `ErpPushService` 统一出口）
| # | 动作 | DTO | 幂等键 | 触发点 |
|---|------|-----|--------|--------|
| 4 | 完工报工回传 | `ErpWorkReportDTO` | 过站记录 id（biz_type=WORK_REPORT） | execution 过站报工 PASS 后 |
| 5 | 领退料回传 | `ErpPickingDTO` / `ErpReturnDTO` | 领料单/退料单 id | material 单据审核后 |
| 6 | 质检结果回传 | `ErpQualityResultDTO` | 检验单 id | quality 检验单提交后 |

### 1.3 可靠性机制（int_push_log，V801）
- 每次回传先落 PENDING 日志（biz_type+biz_id 唯一索引兜底幂等）→ 调客户端 → SUCCESS/FAILED 回写；
- 失败不阻断业务主流程；`ErpPushRetryJob` 每 5 分钟重试 FAILED 且 retry_count < max_retry(5) 的记录，单轮上限 50 条；
- 对账：`GET /integration/push-logs`（权限 `integration:pushlog:list`）按状态筛 FAILED 即待补偿清单，payload 列保留完整报文快照。

## 2. auth-center 用户同步（对账式）
- 契约：`AuthCenterClient.fetchUsers(since)/disableUser/enableUser`（stub 默认）；
- `AuthUserSyncService.sync()`：按工号 `employeeNo` 对账 upsert `sys_user`——新用户建档（随机初始口令 BCrypt 占位，登录走 SSO；dataScope=SELF），同步姓名、岗位、联系方式、入职/离职日期和中心建档时间，`active=false` 置 status=0（软禁用副本，主账号生命周期只在 auth-center）；
- `AuthUserSyncJob` 在应用启动后立即执行一次，随后默认每 60 秒自动全量对账；用户管理页不展示同步按钮。`POST /integration/auth/sync` 只作为运维兜底。
- MES 用户管理默认按入职日期倒序、离职员工置底，支持姓名/工号/账号/岗位、应用状态、在职状态、来源和入职日期筛选及表头排序；子系统管理员不新增或删除员工，只维护 MES 角色、数据范围、基础档案、密码和应用级启停。
- 中心分配、离职、全局停用、应用启停和移除会生成站内消息，在右上角按“集团通知/本系统操作”展示操作时间、员工、工号、入职日期、岗位和联系方式；应用级禁用后已有 JWT 在下一次请求立即失效。

## 3. OIDC 客户端（授权码 + PKCE，SF 仓库契约）
- `GET /integration/oidc/authorize-url`：生成 state + code_verifier（Redis `mes:oidc:state:{state}`，10 分钟一次性，防 CSRF/重放），返回 auth-center `/oauth2/authorize` 完整跳转 URL（S256）；
- `POST /integration/oidc/token`（code+state）：校验并消费 state 后换令牌（stub 返回占位令牌；http 模式接 /token + JWKS 校验 + roles claim 映射本地 JWT 会话 —— TODO 待 sf-auth 端点冻结）；
- `GET /sso/callback`：预留透传端点（安全白名单内），前端回调落地页调用 /token。

## 3.5 真实对接 sf-auth（mode=http，已完成）
- **用户同步**：`AuthCenterClientHttpImpl`（client_credentials 换 service token，Redis 缓存至到期前 60s）→ `GET /service/apps/mes-web/users` 对账式落库；匹配键=工号 employeeNo（本地 sys_user.username）；mes.admin → 本地超管角色映射（增删对账）；active=false → status=0。
- **OIDC 登录（主入口）**：MES 登录页直接提交企业账号到 `POST /integration/oidc/password-login`；MES 后端作为可信 BFF 调用认证中心登录、授权接口并在服务端完成授权码+PKCE 换令牌，浏览器不跳出 MES。随后 JWKS(RS256,kid) 验签（aud 强校验；iss 缺失时跳过、存在则校验）→ 工号匹配本地用户 → `AuthService.buildSession` 签发 MES 会话，并注销临时认证中心管理会话。
- **标准跳转兼容**：`authorize-url` + `POST /integration/oidc/login` 仍保留，供外部标准 OIDC 场景或协议联调使用；state 一次性防重放。
- **应用注册**：auth_db.app_client 中 mes-web 的 redirect_uri 须为 `http://localhost:5173/sso/callback`（SPA 回调）。
- **前端**：登录页保持 MES 原生账号/密码交互，统一认证和应用权限校验对用户无感；仅保留弱化的本地应急管理员入口。
- **联调注意**：①角色权限 Redis 缓存 10 分钟；②已应用迁移脚本修改后需手工修 flyway checksum（dev）。

## 4. 配置（application.yml `mes.integration.*`）
见 `IntegrationProperties`：`erp.mode/base-url/inbound-token/retry-enabled`、`auth-center.mode/base-url/client-id/client-secret/redirect-uri/service-token`。ERP/auth-center 就绪后将 mode 置 http 并补真实客户端实现（ErpClient/AuthCenterClient 各加 @ConditionalOnProperty 实现类即可，契约不变）。

## 5. 边界与依赖
- integration 依赖 plan/basedata/system（入站落地），execution/material/quality 调用其 ErpPushService（出站出口唯一），依赖方向单向无环；
- 普通角色/菜单/数据权限仍在 MES 管理端内部维护，auth-center 只管身份与进门权。
