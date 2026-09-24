# MES 与 ERP 协同设计

> 本文档是 MES（本仓）与 ERP（`../sf-erp`）集成的唯一契约来源。实现落在 `mes-biz-integration`（P2），ERP 侧未实现前先做契约 + stub。

## 1. 系统边界

| 维度 | ERP（计划层） | MES（执行层） |
|---|---|---|
| 核心对象 | 销售订单、MRP、生产订单、采购、库存账、物料账、成本 | 工单（工厂数量级）、排程、派工、过站、WIP、车间质检、批次/SN、领退料、OEE、安灯 |
| 时间粒度 | 日/周/月 | 分钟/过站事件 |
| 物料口径 | 财务库存（按仓库/批次账） | 现场物料（线边仓/超缴/损耗） |
| 质量 | 来料/成品检验结论（记账依据） | 全过程质检（首检/巡检/完工检）+ SPC |
| 成本 | 归集核算 | 只回传数量/工时等事实数据 |

原则：**同一份数据只有一个事实源**。生产订单、物料/BOM/工艺路线主数据以 ERP 为源；过站报工、WIP、车间质检、批次追溯以 MES 为源。双向同步不回写对方源数据的业务状态字段，只做状态镜像（如 ERP 生产订单显示 MES 执行进度）。

## 2. 六类集成接口

方向约定：**下推** = ERP → MES；**回传** = MES → ERP。

| # | 接口 | 方向 | 触发 | 说明 |
|---|---|---|---|---|
| 1 | 主数据同步（物料/BOM/工艺路线/客户/供应商） | 下推 | ERP 变更后推送 + 每日全量对账 | MES 本地副本表，`erp_rev` 记录版本，幂等 upsert |
| 2 | 生产订单下推 | 下推 | ERP 生产订单下达（审核通过） | 转 MES 工单（草稿），带物料/BOM 版本/数量/交期 |
| 3 | 工单进度回传 | 回传 | 状态机变更（开工/完工/关闭/红冲） | 回传数量（合格/不合格/报废）与工时，ERP 更新生产订单执行状态 |
| 4 | 完工报工回传 | 回传 | 过站末道工序报工 | 按工艺路线汇总入库数量，ERP 生成产成品入库单 |
| 5 | 领退料回传 | 回传 | MES 领料/退料/补料单审核 | ERP 生成材料出库/退库单，扣减线边与仓库账 |
| 6 | 质检结果回传 | 回传 | IQC/OQC 判定完成 | ERP 按检验结论做合格入库/退货处理 |

接口形态：REST + JSON，统一信封 `{ source, bizType, bizId, eventTime, payload, retryCount }`，`bizType+bizId+eventTime` 做幂等键。

## 3. 幂等与可靠投递

- **幂等**：接收方按幂等键落 `integration_message_log`（唯一索引），重复消息直接返回成功并标记重复；业务处理与消息日志在同一事务。
- **失败重试**：发送方落 `integration_outbox`（待发送→成功/死信），指数退避重试 5 次（1m/5m/30m/2h/6h），死信人工处理。
- **对账补偿**：每日定时全量对账（主数据按 `erp_rev`，单据按状态+数量），差异生成补偿任务，管理端可视。
- **时序**：同一单据的消息按 `eventTime` 串行消费（按 bizId 分区），避免乱序覆盖。

## 4. 认证与账号体系（auth-center）

> 统一认证中心是独立仓库 `../sf-auth`（由专门会话建设），不是 MES 或 ERP 的职责；MES 与 ERP 的互信经由 auth-center 统一登录建立，不再使用"ERP 账号源头 + 共享 JWT"方案。

- **身份主数据在 auth-center**：账号生命周期（入职/转岗/离职）、应用进门权（该用户可进哪些应用）、`mes.admin` 标签（=MES 本地超管）均只在 auth-center 维护。
- **MES 以 OIDC 客户端接入**：授权码 + PKCE；登录跳转 auth-center，回调换令牌（`/sso/callback` 已预留）；本地账号密码直登仅作认证中心不可用时的降级。
- **用户/组织同步**：MES 调 auth-center 服务间 API 同步"已分配到本应用"的用户/组织为本地副本（sys_user/sys_dept）；对账式同步，主账号软删除 → 本地 active=false；MES 管理端可对本应用用户做禁用/启用（转调 auth-center 应用级启停接口）。
- **权限分治**：普通角色（报工员/质检员等）与菜单/按钮/数据权限完全在 MES 管理端内部维护（RBAC + `@RequirePerm`）；`mes.admin` 标签映射本地超管（通配全部菜单）。
- auth-center 端点契约以其仓库 docs 为准（client_id、/authorize、/token、JWKS、roles claim）；未就绪前 MES 用契约 stub 开发联调。

## 5. 部署拓扑

```
auth-center（统一认证中心，独立部署）
   ├── 统一登录页 / OIDC 授权码+PKCE
   └── 用户/组织主数据 + 应用注册表 + mes.admin 标签
        ↑ 同步副本                ↑ OIDC
ERP（../sf-erp）                  MES（本仓）
 erp-web ── erp-backend ── erp_db
              └── integration ⇄ MES integration REST（生产订单/主数据下推，报工/领退料/质检回传）
                  ├── mes-web ── mes-backend ── mes_db
                  └── /m 移动门户（车间 PDA/平板）
```

ERP 与 MES **分开部署、独立库**，仅通过 integration REST 接口协同；登录互信由 auth-center 提供。任一系统宕机不影响另一系统已落地数据的完整性（靠 outbox + 对账最终一致）。

## 6. ERP 侧未就绪时的 stub 策略

- MES 侧先落契约：接口 DTO、幂等日志、outbox、对账框架全部就位。
- ERP 端点配置化（`mes.integration.erp.base-url`）；stub 模式下发送直接落 outbox 并标记 `mock`，接收方向提供 stub Controller 模拟订单下推，用于联调与演示。
