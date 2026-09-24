# 统一认证中心接入（SSO / sf-auth）

> 版本：V1043（sys_user.employee_no）｜包：`com.erp.system.authcenter`（erp-platform-system）
> 契约唯一事实源：`sf-auth/docs/业务系统接入指南.md`（OIDC 授权码+PKCE / JWKS / 服务间 SCIM 同步）。

## 1. 设计概述

ERP 作为业务系统接入集团统一认证中心（sf-auth，issuer=`http://localhost:8082/api`）：

- **登录**：OIDC 授权码 + PKCE（S256）。认证中心只负责身份认证与 `erp.admin` 角色标签；
  ERP 本地仍用自己的 JWT（JwtTokenProvider）维持会话 —— SSO 只发生在登录桥接一刻，
  后续所有请求与既有账密登录完全同构（token/user/tenantId/orgId/orgs）。
- **用户供给**：SCIM 对账式同步（每小时定时 + 手动触发）。匹配键 = 工号
  `employee_no`（不可变），本地落在 `sys_user.employee_no`（V1043 新列，唯一索引
  `uk_employee_no` 允许 NULL）。`active=false`（LEFT/SUSPENDED/APP_DISABLED）→
  本地 `status=0` 软禁用，不物理删。
- **超管映射**：中心角色标签 `erp.admin` ↔ 本地内置超管角色 `super_admin`
  （sys_user_role 对账式增删；`roles` 含 `erp.admin` 即按超管登录）。

## 2. 登录时序

```
浏览器            ERP前端(5173)         ERP后端(8080/api)        sf-auth(8082/api)      auth-web(5174)
  |--点击统一登录-->|                      |                        |                      |
  |                |--GET /auth/sso/authorize                    |                      |
  |                |  state+PKCE verifier → Redis 5min           |                      |
  |                |<--{authorizeUrl,state}                      |                      |
  |<==整页跳转统一登录页=============================================================>|
  |                用户在统一登录页认证、授权                                                                    |
  |<==重定向 /sso/callback?code&state==========================================================================|
  |                |--POST /auth/sso/callback {code,state}      |                      |
  |                |                     |--POST /oauth/token(Basic erp-web:secret,code_verifier)-->|
  |                |                     |<--access_token/id_token                     |
  |                |                     |--GET /.well-known/jwks.json(缓存,按kid)---->|
  |                |                     |   RS256 验签 + iss/aud/exp 校验              |
  |                |                     |   employee_no → sys_user 匹配(同步副本)      |
  |                |                     |   JwtTokenProvider 签发本地 ERP JWT          |
  |                |<--LoginVO(token/user/tenantId/orgId/orgs)   |                      |
  |                |--setAuth + fetchMe → 进门户                  |                      |
```

- state 一次性消费（Redis `authcenter:sso:state:{state}` → verifier，TTL 5 分钟），
  防 CSRF/重放；授权码本身一次性由中心保证。
- 未匹配本地用户 / 本地已禁用 → 明确业务异常（i18n `erp.system.sso.*`）。
- iss 校验放宽：中心令牌缺失 iss 时跳过，存在则必须等于 `auth.center.base-url`。

## 3. 用户同步（SCIM 对账）

- 定时：`AuthUserSyncJob`（cron `auth.center.sync-cron`，默认每小时整点；
  `auth.center.sync-enabled=false` 可整体关闭；失败仅记日志，下周期重试，对账无状态）。
- 手动：`POST /auth/sso/sync`（`@RequirePerm("system:user:edit")`，
  V1043 种子按钮"同步认证中心用户"挂用户管理下）。
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
    redirect-uri: http://localhost:5173/sso/callback
    sync-enabled: true
    sync-cron: "0 0 * * * ?"
```

## 5. 代码地图

| 位置 | 职责 |
|------|------|
| `authcenter/config/AuthCenterProperties` | `@ConfigurationProperties("auth.center")` |
| `authcenter/oidc/PkceUtil`、`OidcAuthorizeVO` | PKCE S256 工具 / 跳转 VO |
| `authcenter/service/impl/SsoLoginServiceImpl` | 授权 URL、换令牌、JWKS 验签、本地签发 LoginVO |
| `authcenter/client/AuthCenterClient(HttpImpl)` | 服务间 API：service token / SCIM 用户 / 应用级启停 |
| `authcenter/service/impl/AuthUserSyncServiceImpl` | upsert + 软禁用 + 超管角色对账 |
| `authcenter/schedule/AuthUserSyncJob` | 每小时定时同步 |
| `authcenter/controller/SsoAuthController` | `/auth/sso/authorize|callback|sync` |
| `authcenter/mapper/AuthCenterUserMapper` | employee_no 查询（禁租户插件） |
| SQL：`script/sql/migration/V1043__sys_user_employee_no.sql` | 加列 + uk_employee_no + 演示回填(E1001/E1002) + 菜单种子 |
| 前端：`pages/sso/Callback.tsx`、`router/modules/sso.tsx`、`api/auth.ts` | 回调页 / 路由片段 / API |

## 6. 共享文件待接线（主线程）

1. **SecurityConfig**（`erp-backend/erp-base/erp-framework/src/main/java/com/erp/framework/config/SecurityConfig.java`）
   白名单数组追加：`"/auth/sso/authorize", "/auth/sso/callback"`
   （sync 不加白名单，登录后按权限码访问）。
2. **router/index.tsx** 顶层路由表 `/login` 同级追加：
   `...toRouteChildren(ssoRoutes),`，并在模块导入区加
   `import { ssoRoutes } from '@/router/modules/sso'`。

## 7. 降级策略

- 认证中心不可达：登录页本地账密登录保留（不受影响）；SSO 按钮/回调页报明确
  i18n 错误，回调页提供"返回账号密码登录"入口。
- 同步失败：定时任务记日志下周期重试；手动触发返回失败详情；本地用户档案不受影响。
- 令牌问题（state 过期/验签失败/未同步/已禁用）：一律明确业务异常文案，不静默降权。

## 用户管理入口收敛（2026-09-06，对齐 MES V20）

身份生命周期（建号/改密/删除）不再由本系统提供，接口仅剩：查询、分配角色、绑定组织、数据范围调整（`PUT /system/users/{id}/scope`，五档数据权限属本系统授权）、同步（`POST /auth/sso/sync`）、应用级启停（`PUT /auth/sso/users/{id}/app-status`，转调中心 service API 仅影响本系统；无工号的本地降级账号仅本地翻转）。菜单按钮权限 system:user:add/delete/resetPwd 已由 V1044 移除。前端用户页为同步副本视图（工号/本地标记、同步按钮、启停二次确认、角色与数据范围弹窗）。
