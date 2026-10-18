# 统一认证中心接入（SSO / sf-auth）

> 版本：V1043（sys_user.employee_no）｜包：`com.erp.system.authcenter`（erp-platform-system）
> 契约唯一事实源：`sf-auth/docs/业务系统接入指南.md`（OIDC 授权码+PKCE / JWKS / 服务间 SCIM 同步）。

## 1. 设计概述

ERP 作为业务系统接入集团统一认证中心（sf-auth，issuer=`http://localhost:8082/api`）：

- **登录**：ERP 登录页直接展示企业账号/密码表单，浏览器不跳出 ERP。ERP 后端作为
  可信 BFF 代为完成认证中心登录与 OIDC 授权码 + PKCE（S256）交换；认证中心只负责身份认证与 `erp.admin` 角色标签；
  ERP 本地仍用自己的 JWT（JwtTokenProvider）维持会话 —— SSO 只发生在登录桥接一刻，
  后续所有请求与既有账密登录完全同构（token/user/tenantId/orgId/orgs）。
- **用户供给**：SCIM 对账式同步（启动即同步 + 默认每分钟自动对账，页面不提供手动同步）。匹配键 = 工号
  `employee_no`（不可变），本地落在 `sys_user.employee_no`（V1043 新列，唯一索引
  `uk_employee_no` 允许 NULL）。`active=false`（LEFT/SUSPENDED/APP_DISABLED）→
  本地 `status=0` 软禁用，不物理删。
- **超管映射**：中心角色标签 `erp.admin` ↔ 本地内置超管角色 `super_admin`
  （sys_user_role 对账式增删；`roles` 含 `erp.admin` 即按超管登录）。

## 2. 登录时序

```
浏览器            ERP前端(5172)         ERP后端(8080/api)        sf-auth(8082/api)
  |--输入企业账号-->|                      |                        |
  |                |--POST /auth/sso/password-login ------------>|
  |                |                     |--POST /auth/login ---->|
  |                |                     |--POST /oauth/authorize（PKCE + consent）--->|
  |                |                     |<--一次性 code----------|
  |                |                     |--POST /oauth/token(Basic erp-web:secret,code_verifier)-->|
  |                |                     |<--access_token/id_token                     |
  |                |                     |--GET /.well-known/jwks.json(缓存,按kid)---->|
  |                |                     |   RS256 验签 + iss/aud/exp 校验              |
  |                |                     |   employee_no → sys_user 匹配(同步副本)      |
  |                |                     |   JwtTokenProvider 签发本地 ERP JWT          |
  |                |                     |--POST /auth/logout（注销临时中心会话）------>|
  |                |<--LoginVO(token/user/tenantId/orgId/orgs)   |                      |
  |                |--setAuth + fetchMe → 进门户                  |                      |
```

- state 一次性消费（Redis `authcenter:sso:state:{state}` → verifier，TTL 5 分钟），
  防 CSRF/重放；授权码本身一次性由中心保证。
- 未匹配本地用户 / 本地已禁用 → 明确业务异常（i18n `erp.system.sso.*`）。
- iss 校验放宽：中心令牌缺失 iss 时跳过，存在则必须等于 `auth.center.base-url`。

## 3. 用户同步（SCIM 对账）

- 定时：`AuthUserSyncJob`（应用启动后立即执行一次；cron `auth.center.sync-cron`，默认每分钟；
  `auth.center.sync-enabled=false` 可整体关闭；失败仅记日志，下周期重试，对账无状态）。
- 运维兜底：保留 `POST /auth/sso/sync`（`@RequirePerm("system:user:edit")`），
  但用户管理页不展示同步按钮，日常人员供给无需人工操作。
- 流程：`client_credentials`（Basic erp-web:secret）换 service token
  （Redis 缓存 `authcenter:service-token`，到期前 60 秒刷新）→
  `GET /service/apps/erp-web/users` → upsert：
  - 新增：username=employee_no、nickname=name、随机密码（BCrypt 占位，不可本地登录）、
    status=1、默认租户 1000/总厂 1、dataScope=SELF；
  - 更新：nickname 刷新、status 按 active 对账；
  - 未建档且已失效：只计数不落死数据；
  - `erp.admin` ↔ `super_admin` 角色绑定双向对账。
- 返回 `{added,updated,disabled,total}`。

## 4. 配置项（application.yml）

```yaml
auth:
  center:
    base-url: http://localhost:8082/api        # issuer
    authorize-page-url: http://localhost:5174/oauth/authorize
    client-id: erp-web
    client-secret: demo-secret                 # 生产走环境变量
    redirect-uri: http://localhost:5172/sso/callback
    sync-enabled: true
    sync-cron: "0 * * * * ?"
```

## 5. 代码地图

| 位置 | 职责 |
|------|------|
| `authcenter/config/AuthCenterProperties` | `@ConfigurationProperties("auth.center")` |
| `authcenter/oidc/PkceUtil`、`OidcAuthorizeVO` | PKCE S256 工具 / 跳转 VO |
| `authcenter/service/impl/SsoLoginServiceImpl` | 原生企业账号登录、授权 URL、换令牌、JWKS 验签、本地签发 LoginVO |
| `authcenter/client/AuthCenterClient(HttpImpl)` | 服务间 API：service token / SCIM 用户 / 应用级启停 |
| `authcenter/service/impl/AuthUserSyncServiceImpl` | upsert + 软禁用 + 超管角色对账 |
| `authcenter/schedule/AuthUserSyncJob` | 启动即同步 + 每分钟自动对账 |
| `authcenter/controller/SsoAuthController` | `/auth/sso/password-login|authorize|callback|sync` |
| `authcenter/mapper/AuthCenterUserMapper` | employee_no 查询（禁租户插件） |
| SQL：`script/sql/migration/V1043__sys_user_employee_no.sql` | 加列 + uk_employee_no + 演示回填(E1001/E1002) + 菜单种子 |
| 前端：`pages/login/Login.tsx`、`api/auth.ts` | ERP 原生登录页 / 企业账号登录 API |

## 6. 共享文件待接线（主线程）

1. **SecurityConfig**（`erp-backend/erp-base/erp-framework/src/main/java/com/erp/framework/config/SecurityConfig.java`）
   白名单数组追加：`"/auth/sso/password-login", "/auth/sso/authorize", "/auth/sso/callback"`
   （sync 不加白名单，登录后按权限码访问）。
2. **router/index.tsx** 顶层路由表 `/login` 同级追加：
   `...toRouteChildren(ssoRoutes),`，并在模块导入区加
   `import { ssoRoutes } from '@/router/modules/sso'`。

## 7. 降级策略

- 认证中心不可达：登录页提供弱化的“应急管理员登录”入口；切换后使用 ERP 本地账号，
  企业账号登录返回明确 i18n 错误。应急入口不是日常登录方式。
- 同步失败：定时任务记日志下周期重试；手动触发返回失败详情；本地用户档案不受影响。
- 令牌问题（state 过期/验签失败/未同步/已禁用）：一律明确业务异常文案，不静默降权。

## 用户管理入口收敛（2026-10-17，对齐 MES）

认证中心负责员工建号、应用分配、离职和删除；ERP 不新增或删除员工。ERP 管理员可维护本系统角色、组织和数据范围，可通过受控接口把基础档案与密码变更写回认证中心，并执行仅影响 ERP 的应用级启停。用户页默认按入职日期倒序、离职员工置底，支持姓名/工号/账号/岗位、应用状态、在职状态、来源和入职日期筛选及表头排序。

认证中心分配、离职、全局停用、应用启停和移除会在下一轮自动对账时转换为 ERP 站内消息，右上角消息入口展示“集团通知/本系统操作”来源、操作时间、员工、工号、入职日期、岗位和联系方式。应用级禁用成功后，后端在每次请求校验用户状态，已有 JWT 会立即返回 401 并由前端退出登录。
