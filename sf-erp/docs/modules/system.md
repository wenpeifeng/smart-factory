# erp-system 系统基座模块文档

> 模块：认证授权 / 用户 / 角色 / 菜单（RBAC）/ 部门 / 数据字典 / 操作日志
> 版本：P0 首版（2026-09-05） · 依赖：erp-framework（Security/MP/Redis/JWT）

## 1. 功能清单

| 功能 | 说明 |
|---|---|
| JWT 认证 | POST /auth/login 签发 HS256 令牌；Redis 存在线会话 `token:{userId}:{token}`；登出写黑名单 `auth:blacklist:{token}`；连错 5 次锁 10 分钟 |
| 用户管理 | 分页/详情/新增/修改/删除/重置密码/启停/分配角色；出参 VO 永不含 password |
| RBAC | 角色 CRUD、菜单三级树（目录/菜单/按钮）CRUD、角色分配菜单（树勾选整体覆盖）、用户分配角色；内置 super_admin 全量放行 |
| 部门 | 树形 CRUD（parent_id 自关联，防环校验，删除保护） |
| 字典 | 字典类型 + 字典项 CRUD；GET /dict/{type} 公开选项（白名单） |
| 操作日志 | @OperLog(module, operation) 注解 + AOP 切面自动落库；分页查询 |

## 2. 表清单（script/sql/migration/V11~V15）

| 表 | 脚本 | 说明 |
|---|---|---|
| sys_dept | V11 | 部门树（parent_id 自关联，0 为根） |
| sys_role | V12 | 角色（uk_role_code；super_admin 内置） |
| sys_menu | V12 | 菜单三级树：type 1目录/2菜单/3按钮；path 存完整路由（如 system/users）；component 存前端组件路径（相对 pages/） |
| sys_user_role | V12 | 用户-角色关联（重分配物理删除） |
| sys_role_menu | V12 | 角色-菜单关联（重分配物理删除） |
| sys_dict_type | V13 | 字典类型（uk_dict_type） |
| sys_dict_data | V13 | 字典项（dict_type 编码字符串关联） |
| sys_oper_log | V14 | 操作日志（模块/操作/方法/参数/操作人/IP/耗时/结果） |
| 初始数据 | V15 | 部门树、super_admin 角色、系统管理菜单树（含按钮权限）、初始超管账号 erp_admin/123456（套件统一账号规范）、内置字典 sys_common_status / sys_yes_no |

sys_user 沿用 V1，未改动结构。

## 3. 接口清单

context-path 为 `/api`，除白名单外均需 `Authorization: Bearer {token}`。

### 3.1 认证（/auth）

| 方法 | 路径 | 说明 | 备注 |
|---|---|---|---|
| POST | /auth/login | 登录（LoginForm: username/password → LoginVO: token + user） | 白名单 |
| POST | /auth/logout | 登出（删 Redis 会话 + 拉黑令牌） | |
| GET | /auth/me | 当前用户上下文（user + roles + perms） | |
| GET | /auth/menus | 当前用户可见菜单树（目录+菜单） | |

### 3.2 用户（/system/users）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /system/users | 分页（keyword/deptId/status/pageNum/pageSize），行含部门名与角色聚合 |
| GET | /system/users/{id} | 详情（含 roleIds/roleNames 回显） |
| POST | /system/users | 新增（密码必填 BCrypt；可同时分配角色） |
| PUT | /system/users/{id} | 修改（密码留空不变；roleIds 非 null 时重分配） |
| DELETE | /system/users/{id} | 逻辑删除（admin 与自己不可删） |
| PUT | /system/users/{id}/password | 重置密码 |
| PUT | /system/users/{id}/status | 启用/停用（admin 与自己不可停用） |
| PUT | /system/users/{id}/roles | 分配角色（整体覆盖） |

### 3.3 角色（/system/roles）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /system/roles | 分页（keyword/status） |
| GET | /system/roles/all | 全量列表（用户分配角色下拉） |
| GET | /system/roles/{id} | 详情 |
| POST | /system/roles | 新增（code 唯一） |
| PUT | /system/roles/{id} | 修改（super_admin 编码不可改） |
| DELETE | /system/roles/{id} | 删除（super_admin 不可删；被用户引用拒绝） |
| GET | /system/roles/{id}/menus | 已分配菜单 ID 列表（回显） |
| PUT | /system/roles/{id}/menus | 分配菜单（整体覆盖，含按钮） |

### 3.4 菜单（/system/menus）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /system/menus | 全量树（keyword 过滤保留祖先链） |
| GET | /system/menus/{id} | 详情 |
| POST | /system/menus | 新增（按类型校验 path/component/perms 必填组合；按钮只能挂菜单下） |
| PUT | /system/menus/{id} | 修改（上级不可选自身/后代） |
| DELETE | /system/menus/{id} | 删除（有子节点或被角色引用拒绝；删除时清理角色关联） |

### 3.5 部门（/system/depts）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /system/depts | 部门树（keyword 过滤） |
| GET | /system/depts/{id} | 详情 |
| POST | /system/depts | 新增 |
| PUT | /system/depts/{id} | 修改（防环校验） |
| DELETE | /system/depts/{id} | 删除（有子部门或挂靠用户拒绝） |

### 3.6 字典（/system/dict/*、/dict）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /system/dict/types | 类型分页（keyword/status） |
| POST | /system/dict/types | 新增类型（type 编码唯一） |
| PUT | /system/dict/types/{id} | 修改（编码变更级联更新字典项 dict_type） |
| DELETE | /system/dict/types/{id} | 删除（有字典项拒绝） |
| GET | /system/dict/datas | 字典项分页（dictType/keyword/status） |
| POST | /system/dict/datas | 新增（同类型下 label/value 唯一） |
| PUT | /system/dict/datas/{id} | 修改 |
| DELETE | /system/dict/datas/{id} | 删除 |
| GET | /dict/{type} | 公开取启用选项 [{label, value, sort}]，**白名单**，其他模块下拉数据源 |

### 3.7 操作日志（/system/logs）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /system/logs | 分页（module/operatorName/status/beginTime/endTime） |

## 4. 集成点说明（其他模块必读）

1. **取当前登录人**：`com.erp.system.security.SecurityUtils.getCurrentUserId()`（返回 Long，未登录 null）。
   MP 审计填充（framework MybatisPlusConfig）已自动从 SecurityContext 取用户 ID 写 create_by/update_by，业务代码无需手工赋值。
2. **字典取数**：前端 `GET /api/dict/{type}`（白名单免登录）；后端注入 `SysDictDataService.listEnabledByType(type)`。
3. **菜单/路由**：sys_menu 的 path 存完整路由（不带斜杠）、component 存前端组件路径（相对 pages/）。
   新模块页面：先在菜单管理录入目录/菜单/按钮，再把路由片段文件挂到前端 router/modules/ 下（system.tsx 为模板），主线程合并进 router/index.tsx。
4. **按钮权限**：按钮 perms 命名 `模块:资源:动作`（如 system:user:add）；GET /auth/me 的 perms 数组供前端 v-perm 控制。
5. **操作日志**：Controller 写方法标注 `@OperLog(module="采购管理", operation="新增订单")`（com.erp.system.annotation.OperLog）即自动落库（含参数、耗时、成败、操作人、IP），落库失败不影响业务。
6. **登录锁定**：Redis `login:fail:{username}` 计数，10 分钟窗口 5 次。
7. **JWT 配置**（可选，默认值已内置）：`erp.jwt.secret`、`erp.jwt.expire-minutes`（默认 480 分钟）；生产必须覆盖 secret。
8. **共享文件改动**（本模块被授权的两处）：
   - `erp-framework/config/SecurityConfig.java`：白名单 `/auth/login /ping /v3/api-docs/** /swagger-ui/** /dict/* /error /actuator/health`（+OPTIONS 预检），其余 authenticated；401 统一返回 Result JSON（code=401）；JWT 过滤器按 Bean 名 `jwtAuthFilter` 从容器查找挂链（避免 framework 反向依赖 system）。
   - `erp-framework/config/MybatisPlusConfig.java`：审计填充 currentUserId() 改为从 SecurityContext 读取 principal（约定为 Long 用户 ID，见 JwtAuthFilter Javadoc）。
9. **前端**：`src/api/system.ts`（全部接口+类型）、`src/router/modules/system.tsx`（导出 systemRoutes：system/users|roles|menus|depts|dicts|logs 六页）；页面在 `src/pages/system/` 下，TanStack Query + AntD Table/Modal/Tree。

## 5. 验证记录（2026-09-05 实测）

- `cd erp-backend && mvn -pl erp-system -am compile -q` 通过。
- `cd erp-web && npx tsc --noEmit` 通过（0 error）。
- Flyway：V11~V15 全部 success；23 条菜单、23 条角色菜单、admin 绑定 super_admin、内置字典就位。
- POST /auth/login（erp_admin/123456）→ token + 用户信息。
- 无 token 访问 /system/users → HTTP 401 + `{"code":401,"message":"未登录或登录已过期"}`。
- GET /auth/menus → 系统管理目录 + 6 个菜单的树；GET /auth/me → roles=[super_admin]、16 个按钮 perms。
- 用户 CRUD/重置密码/启停/分配角色、角色分配菜单（回显+覆盖）、菜单树、部门树、字典类型/字典项 CRUD 全部通过；
  UserVO 出参无 password 字段；JSR-303 校验（400）/重复账号（1000）按预期。
- 登录防爆破：连错 5 次后锁定提示，正确密码也被拒（Redis login:fail 计数验证）。
- 登出：Redis `token:{userId}:{token}` 删除、`auth:blacklist:{token}` 写入，旧 token 立即 401。
- 公开字典 GET /dict/sys_common_status（免登录）→ [{label:启用,value:1},{label:停用,value:0}]。
- 操作日志：@OperLog 切面自动记录（登录/用户管理等），分页查询与 module/operatorName 过滤可用。
- 前端：Vite 代理 (5173→8080) 登录打通，Login.tsx「开发模式进入」按钮已删除。

### 已知说明

- 登录/登出类操作日志的 operator 为空（发生时无认证上下文），属预期。
- 集成 jar 曾被兄弟模块的 Bean 冲突（finance/inventory BizNoGenerator、purchase/inventory InvStockMapper）阻塞，
  相关模块已各自修复；验证时如再遇冲突以各模块最新 install 为准。
