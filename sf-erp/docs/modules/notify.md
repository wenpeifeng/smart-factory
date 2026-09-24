# 统一通知中心（erp-notify）设计说明

> 模块：erp-backend/erp-platform/erp-platform-notify + erp-web/src/pages/system/notify（管理门户配置页）+ erp-web/src/mobile/ApprovePage.tsx（移动审批页）
> SQL：script/sql/migration/V1051__notify_init.sql（版本段 V105x）
> 状态：通道闭环（webhook 真实可测；钉钉/企微配置即通），workflow 侧事件接入完成

## 1. 功能范围

| 功能 | 表/入口 | 说明 |
|---|---|---|
| 待办推送 | TaskCreatedEvent → 各通道 | 任务生成点发事件，推送文案含单据类型+单号+金额+发起人+审批链接 |
| 手机审批回写 | /m/approve/{taskId} + /notify/callback/* | H5 直审（调既有 approve/reject 接口）为主，开放平台回调验签路由为预留 |
| 预警群通知 | WarnEvent → webhook 群机器人 | Markdown 卡片：预警类型/内容/时间 |
| 通道管理 | notify_channel_config | CRUD/启停/测试发送/发送日志，凭据脱敏（仅末 4 位） |
| 用户绑定 | notify_user_binding | 钉钉=手机号、企微=成员 userid；未绑定该通道跳过并降级站内信 |
| 发送日志 | notify_send_log | 每次外发（含重试收口）记录内容/结果/耗时 |

## 2. 架构（通道 SPI + 事件订阅）

```
workflow 业务点                          notify 模块
─────────────────────                  ──────────────────────────────
WorkflowServiceImpl.dispatchNode   ──▶  TaskCreatedEvent ─┐
WorkflowServiceImpl.approve/reject ──▶ ApprovalResultEvent ├─▶ NotifyServiceImpl（@TransactionalEventListener AFTER_COMMIT）
WfWarnServiceImpl 三类扫描         ──▶  WarnEvent ────────┘        │ 按事件位(event_mask)找启用通道（60s 缓存）
                                                                │ webhook → 群机器人直接发
                                                                │ dingtalk/wecom → notify_user_binding 找人 → 发
                                                                │ 失败重试 1 次 → notify_send_log + 降级 wf_message
```

依赖方向：**erp-workflow → erp-platform-notify**（仅事件契约 com.erp.notify.event.NotifyEvents 与
回调 SPI com.erp.notify.core.ApprovalCallbackHandler）；notify 不 import workflow 任何代码，
站内信降级经 NotifyRefMapper 直写 wf_message（与 WfRefMapper 同一"约定表结构、不 import 代码"约定）。

### 事件清单（workflow 侧 publish 点）

| 事件 | 发布位置 | 接收人 | 事件位 |
|---|---|---|---|
| TaskCreatedEvent | WorkflowServiceImpl.dispatchNode（每审批人一条） | 任务审批人 | bit0=1 |
| ApprovalResultEvent | approve 最终通过 / reject 驳回（实例终态） | 发起人 | bit1=2 |
| WarnEvent | WfWarnServiceImpl 三类扫描（24h Redis 去重后） | admin(id=1) | bit2=4 |

监听使用 `@TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=true)`：
业务事务提交后才外发（回滚不产生幽灵通知）；预警定时任务无事务，立即执行。

### 降级语义（与 workflow 站内信互补，不重复兜底）

- **无启用通道**：workflow 自身的 wf_message 已是站内信（NotifyService 只记 debug 日志）；
- **通道启用但发送失败（重试 1 次后）或接收人未绑定外部账号**：写发送日志 + log.warn +
  补写一条【通道降级】wf_message（接收人在消息中心仍可见）；群通道（webhook）失败无个人降级对象，
  仅日志与 warn。

## 3. 通道类型与凭据

| 类型 | 凭据（credentials JSON） | 必填 | 说明 |
|---|---|---|---|
| webhook | webhookUrl | webhookUrl | 钉钉自定义机器人/企微群机器人通用（markdown 三键合体报文：title/text/content，两家互忽略对方键），配置即可真实发送 |
| dingtalk | corpId, agentId, appSecret（可选 aesKey, token） | 前三者 | gettoken（缓存+提前刷新）→ 手机号 getbymobile 换 userid（缓存）→ asyncsend_v2 工作通知 Markdown |
| wecom | corpId, agentId, secret（可选 encodingAesKey, token） | 前三者 | gettoken（缓存）→ message/send（touser=绑定 userid，Markdown） |

凭据安全：接口返回一律脱敏（`****` + 末 4 位；webhookUrl 保留协议+host）；修改时空值/掩码串
（****开头）=保持原值。`event_mask` 位：1 待办 / 2 审批结果 / 4 预警（默认 7 全部）。

待办推送链接 = `mobile_base_url` + `/m/approve/{taskId}`（每通道独立配置移动端基地址，
未配置则不带链接行）。

## 4. API 清单（context-path=/api）

### 通道管理 /notify/channels（权限 system:notify:*）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /notify/channels | 分页：keyword/channelType/status；凭据脱敏返回 |
| POST | /notify/channels | 新增（webhook 必填 url；钉钉/企微按类型校验必填凭据） |
| PUT | /notify/channels/{id} | 修改（凭据空/掩码=保持原值） |
| PUT | /notify/channels/{id}/status?status= | 启用/停用 |
| DELETE | /notify/channels/{id} | 删除（逻辑删） |
| POST | /notify/channels/{id}/test?receiverUserId= | 测试发送一条 Markdown（钉钉/企微需接收人已绑定） |

### 日志与绑定
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /notify/send-logs | 分页：channelId/channelType/eventType/success |
| GET | /notify/bindings | 绑定分页（带用户昵称） |
| POST | /notify/bindings | 新增/覆盖绑定 {userId, channel, externalId} |
| DELETE | /notify/bindings/{id} | 删除绑定 |

### 开放平台回调（安全白名单放行，验签保护）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /notify/callback/wecom | 企微 URL 验证：sha1(token,timestamp,nonce,echostr) 验签 + AES 解密回显 echostr |
| POST | /notify/callback/wecom | 企微事件推送（XML），审批动作报文路由到 ApprovalCallbackHandler |
| POST | /notify/callback/dingtalk?sign=&timestamp=&nonce= | 钉钉事件订阅（加密 JSON），验签+解密+路由，响应回加密 JSON |

回调报文识别 `taskId` + `action`（approve/agree/1=通过）+ `opinion`，路由到 workflow 的
`WfNotifyCallbackHandler`（以任务审批人身份临时建立 SecurityContext，复用既有 approve/reject 全链路）。

### workflow 侧新增
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /workflow/tasks/{id}/detail | 任务详情（单据/金额/意见历史），移动审批页用 |

## 5. 手机审批（两条路径）

1. **H5 直审（默认，凭据未配也可用）**：推送链接指向 `/m/approve/{taskId}`
   （前端 erp-web/src/mobile/ApprovePage.tsx，路由 m/approve/:taskId，登录守卫复用移动门户）。
   页面展示单据/金额/发起人/意见历史，通过/驳回调既有 `/workflow/tasks/{id}/approve|reject`。
2. **开放平台回调（预留）**：钉钉/企微卡片上的按钮 → /notify/callback/* 验签解密 →
   ApprovalCallbackHandler 回写审批。需在通道凭据中配置 token+aesKey（钉钉）/
   token+encodingAesKey（企微），并在开放平台配置回调 URL。凭据未配时用 H5 直审。

## 6. 通道配置指南（管理门户 /admin/system/notify-channels）

1. **群机器人（最快可用）**：钉钉群 → 群设置 → 智能群助手 → 添加自定义机器人（安全设置选
   自定义关键词可填 "ERP"）→ 复制 webhook 地址；企微群 → 添加群机器人 → 复制 webhook。
   通道类型选"群机器人"，粘贴地址，勾选事件（预警建议只勾"预警"），保存 → 点"测试发送"验证。
2. **钉钉企业内部应用**：open.dingtalk.com → 应用开发 → 创建企业内部应用，记下
   corpId/agentId/appSecret；通讯录中确保成员手机号与"用户绑定"一致（通道按手机号 getbymobile
   换 userid）。权限：企业内发消息（asyncsend_v2）。
3. **企微自建应用**：work.weixin.qq.com → 应用管理 → 创建自建应用，记下 corpId/secret/agentId；
   成员 userid 填进"用户绑定"（企微后台成员详情的账号 ID，通常配置为手机号）。
   权限：发送应用消息（message/send）。
4. **移动端基地址**：填 ERP H5 可访问地址（如 http://erp.example.com:5173），
   待办推送将带 `[点击前往审批](该地址/m/approve/{taskId})` 链接。

## 7. 前端

- `src/api/notify.ts`：通道/日志/绑定 类型与请求（与后端 VO 对齐）；
- `src/pages/system/notify/ChannelList.tsx`：列表/新增修改（凭据按类型条件展示、脱敏回显）/
  启停/测试发送（钉钉企微选接收人）/发送日志 Drawer/用户绑定 Drawer；i18n key `notify.*`（zh/en）；
- `src/mobile/ApprovePage.tsx`：移动审批页（复用 MobileShell 轻布局）；
- 路由：管理门户 `system/notify-channels`（router/index.tsx admin children）、移动端
  `m/approve/:taskId`（router/modules/mobile.tsx，导出名 mobileRoutes 不变）。

## 8. 已知事项 / 遗留

1. **通道配置缓存**：进程内 60s TTL，CRUD 即时失效本节点；多节点部署其余节点最迟 60s 收敛
   （如需即时一致可改 Redis 发布订阅失效广播）。
2. **钉钉 access_token / 手机号→userid** 为进程内缓存（token 7200s 提前 60s 刷新）；多节点
   各自缓存，量级在钉钉频控之内。
3. **回调为预留能力**：仅在通道配置了 token+aesKey 后启用验签解密；未配置时 H5 直审。
   企微 XML 报文解析为最小实现（Extract 字段），完整事件订阅（审批模板回调）按需扩展。
4. **发送日志无清理任务**：量级可控；如需保留期后续加 @Scheduled 清理（同 wf_message 遗留项）。
5. **预警接收人**当前随 workflow 固定 admin（id=1）；如需按角色分发，扩展 WfConstants。
