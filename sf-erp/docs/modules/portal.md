# 三门户架构与登录重设计（前端）

> 版本：v1.0（2026-09-05） · 关联设计：docs/集团组织与数据权限设计.md 第 0.1 章（三门户架构）
> 覆盖：登录页（分屏式 + 两步式）、业务/管理/移动三门户路由、前端 i18n（react-i18next）

## 1. 三门户结构

```
登录页 /login（分屏式现代设计，两步式：账号密码 → 多组织时选择组织）
  │  同一 SSO 认证、同一权限体系（JWT），菜单按门户分流
  ├─ 业务门户 /        MainLayout   95% 用户：工作台/基础资料/采购/销售/库存/质检/
  │                                 财务/成本/生产/资产/审批/人力/设备/发票/预算/报表/组织/打印
  ├─ 管理门户 /admin/* AdminLayout  平台管理员/集团 IT：系统管理（用户/角色/菜单/部门/
  │                                 字典/操作日志）+ 租户管理 + 登录日志（占位）+ 组织工厂
  └─ 移动门户 /m/*     独立轻页面   仓库 PDA 扫码作业（已交付，无侧边栏布局）
```

| 门户 | 布局 | 守卫 | 视觉 |
| --- | --- | --- | --- |
| 业务 / | `src/layouts/MainLayout.tsx` | RequireAuth | 浅色顶栏 + antd 深色侧边栏（湛蓝主色） |
| 管理 /admin | `src/layouts/AdminLayout.tsx` | RequireAuth + RequireAdmin | 深海军蓝渐变侧边栏（#0a1929→#123055）+ 青色点缀，与业务门户强区分 |
| 移动 /m | 无布局（页面自渲染） | RequireAuth | 独立 H5 页面 |

- **管理门户准入口径**（唯一实现 `useAdminAccess()`，src/hooks/usePerm.ts）：`roles` 含 `super_admin` **或** `perms` 含任意 `system:*` 权限码；守卫 `RequireAdmin`（src/router/guards.tsx）与业务门户顶栏「管理门户」入口共用此口径，无权限访问 /admin/* 一律重定向 `/dashboard`。
- 旧 `/system/*` 路径自动重定向到 `/admin/system/*`（书签/外链兼容，router/index.tsx LegacySystemRedirect）。
- 组织工厂（org/factories）页面在业务与管理门户各挂一份路由（同一懒加载组件复用）。

## 2. 登录页（src/pages/login/Login.tsx）

### 2.1 布局与动效

- **分屏**：左 45% 品牌区（深色渐变 #0a1929→#1668dc；Framer Motion 发光数据线条 SVG、
  3 张浮动玻璃指标卡（y 循环浮动 + 错峰入场）、3 条产品价值点轮播 4.2s）；
  右 55% 玻璃拟态 light 登录卡（`rgba(255,255,255,0.72)` + `backdropFilter: blur(20px)`）。
- **微交互**：卡片错峰渐入上浮（staggerChildren 0.09s）；输入框聚焦高亮（容器提亮 +
  底部湛蓝→青渐变高亮线 scaleX 展开）；登录按钮 loading；**密码错误卡片左右抖动**
  （variant `x: [0,-14,14,-10,10,-6,6,-3,3,0]`，onAnimationComplete 复位，可重复触发）。
- **窄屏自适应**：`matchMedia('(max-width: 768px)')` 监听，<768px 隐藏品牌区降为单列，
  登录卡内补品牌头；断点变化实时响应。

### 2.2 两步式登录流程

```
POST /auth/login {username, password}
  │ 响应: {token, user, tenantId, orgId, orgs:[{id,code,name}]}
  │ 写 store（setAuth 带 tenantId/orgId/orgs）→ GET /auth/me 回填 roles/perms
  ├─ orgs.length ≤ 1 ──────────────► 直接进入来源页（默认 /dashboard）
  └─ orgs.length > 1 ─► 第二步：组织卡片选择（hover 上浮，当前默认组织打标）
                          │ 点击组织 → GET /auth/switch-org/{orgId}（重签 JWT，orgId 入 claims）
                          │ setAuth 用新 token 覆盖 → 进入门户
                          └「返回重新登录」回第一步
```

- **记住账号**：localStorage `erp-login-username`，勾选才持久化。
- **租户展示**：响应 `tenantId===1000` 显示「默认租户」标签（i18n: login.tenantDefault），其余显示「租户 {id}」。
- 错误提示由 axios 拦截器统一弹出（可按 Accept-Language 双语），登录页只负责抖动反馈。

## 3. 组织切换（业务门户顶栏）

- 展示条件：`orgs.length > 1`（单组织/移动端登录不渲染）。
- 流程：Dropdown 选组织 → `GET /auth/switch-org/{orgId}` 重签令牌 →
  `setAuth` 覆盖 token/orgId/orgs → **`window.location.reload()` 整页刷新**（清空
  React Query 缓存与页面状态，所有模块数据按新令牌组织上下文自动过滤）。
- 切换期间按钮 spin + loading 提示，防重复点击。

## 4. 前端 i18n 使用规范（react-i18next）

### 4.1 基础设施

| 文件 | 职责 |
| --- | --- |
| `src/locales/index.ts` | i18next 初始化；`changeLang(lang)` 切换并持久化；`acceptLanguage()` 供 axios 头 |
| `src/locales/zh.json` / `en.json` | 资源文件（内置打包，key 集合必须完全一致，zh 为基准） |
| `src/main.tsx` | 挂载 i18next；antd ConfigProvider locale（zh_CN/en_US）与 dayjs locale 随语言联动 |

- 语言持久化：localStorage `erp-lang`，默认 `zh`；切换即时生效（useTranslation 订阅重渲染）。
- 请求头：axios 拦截器统一带 `Accept-Language`（zh-CN/en），后端 message 按此本地化
  （见 docs/modules/i18n.md 第 5 节；后端存量硬编码中文约 450 处迁移中）。

### 4.2 命名空间与使用方式

| 前缀 | 覆盖 |
| --- | --- |
| `login.*` | 登录页全部文案（品牌/表单/两步式/指标/价值点数组） |
| `menu.*` / `admin.*` | 业务门户 / 管理门户菜单名（label 一律 `t(i18nKey)` 渲染） |
| `topbar.*` | 顶栏（组织切换/语言/门户入口/个人中心/退出） |
| `common.*` | 通用操作（新增/编辑/删除/审核/搜索/导出/保存/取消/成功/失败…） |
| `perm.noPerm` | HasPerm 无权提示 |
| `tenant.*` | 租户管理页（管理门户） |

```tsx
// 组件内
const { t } = useTranslation()
t('common.add')                       // 文案
t('tenant.enabledCount', { count: 3 })// 插值
t('login.values', { returnObjects: true }) as { title: string; desc: string }[] // 数组对象

// 切换语言（按钮/下拉统一调它，不要直接调 i18n.changeLanguage）
changeLang('en')
```

- **菜单 i18n 模式**：布局内菜单配置只存 `i18nKey`，`useMemo` 里 `t(key)` 生成 items（依赖 `t`，语言切换自动重算）。
- `HasPerm` 新增 `hint` 属性：无权限时渲染 `t('perm.noPerm')`（默认仍不渲染，向后兼容）。

### 4.3 新增文案流程

1. zh.json 与 en.json **同步**追加（两文件 key 集合必须一致）；
2. 组件 `useTranslation()` 取用；模块专属文案建议按 `menu.<模块>.*` / `<模块>.*` 前缀归组；
3. 禁止在布局/登录/公共组件硬编码中英文文案。

## 5. 代码地图

| 文件 | 说明 |
| --- | --- |
| `src/pages/login/Login.tsx` | 分屏登录页（品牌区/两步式/抖动/记住账号/窄屏自适应） |
| `src/layouts/MainLayout.tsx` | 业务门户：菜单 i18n + 组织切换器 + 语言切换器 + 管理门户入口 |
| `src/layouts/AdminLayout.tsx` | 管理门户：深色布局 + 菜单（系统管理/租户/登录日志/组织工厂） |
| `src/router/index.tsx` | 三门户路由表 + `/system/*` 旧路径重定向 |
| `src/router/guards.tsx` | RequireAuth + RequireAdmin（权限未加载先补拉 /auth/me） |
| `src/hooks/usePerm.ts` | `useAdminAccess()`：管理门户准入唯一口径 |
| `src/stores/auth.ts` | +tenantId/orgId/orgs/setOrgContext（setAuth 第三参可选，兼容移动端） |
| `src/api/auth.ts` | login / fetchMe / switchOrg 类型化封装 |
| `src/pages/system/tenants/TenantList.tsx` | 租户管理（列表/新增/编辑/启停，默认租户保护） |
| `src/pages/system/logs/LoginLogList.tsx` | 登录日志占位（B 任务并行开发中，原位替换） |

## 6. 验证记录（2026-09-05）

- `npx tsc --noEmit` 0 错误；`npx vite build` 通过（chunk 体积警告为存量）。
- 后端实测（经 vite 代理）：登录返回 orgs 3 组织；switch-org/3 后 JWT claims orgId=3；Accept-Language: en 时已迁移接口返回英文。
- dev server 模块断言：Login/MainLayout/AdminLayout/router/locales 等 13 个变更模块编译 200，含分屏/两步式/抖动 keyframes/768px 断点标记。
- 待浏览器端补充（本环境无浏览器工具）：/admin 守卫重定向实景、语言切换菜单英文实景、窄屏视觉。

## 7. 遗留与边界

1. 登录日志页为占位（B 任务开发 sys_login_log，路由/菜单已就位，交付后原位替换）。
2. 管理门户菜单暂未按权限码逐项过滤（准入=任意 system:* 或 super_admin）；租户接口后端未挂 @RequirePerm，后续上权限码后菜单应联动 `hasPerm`。
3. 登录页租户选择器未开放（后端 LoginForm 支持可选 tenantId，`/system/tenants/enabled` 已就绪，多租户登录开放时接上）。
4. 用户-组织绑定表未建（设计文档第 7 章遗留 4），可切换组织列表暂为全部启用组织。
5. 移动端 MobileLogin 仍走旧 setAuth(token, user)（不写组织上下文，组织切换器自动隐藏），待移动端任务统一升级。
