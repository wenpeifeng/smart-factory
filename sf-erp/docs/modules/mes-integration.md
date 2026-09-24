# ERP-MES 集成（erp-biz-integration，V1231）

> 契约唯一事实源：`sf-mes/docs/MES与ERP协同设计.md`。本文档只描述 ERP 侧实现。
> 模块：`erp-backend/erp-biz/erp-biz-integration`（包 `com.erp.integration`），
> DTO 与 MES 侧 `com.mes.integration.erp.dto.*` 逐字段对齐，一字不差。

## 1. 六类接口的 ERP 侧实现状态

| # | 接口 | 方向 | ERP 侧实现 | 状态 |
|---|---|---|---|---|
| 1 | 主数据同步（物料/BOM/工艺路线） | ERP→MES 下推 | `MasterdataSyncService`：每日 02:00 全量对账推送（`MasterdataReconcileJob`）+ 管理端手动触发 `POST /integration/masterdata/push`；每条入 `int_outbox` | 已实现 |
| 2 | 生产订单下推 | ERP→MES 下推 | mrp `doRelease` 发 `WorkOrderReleasedEvent`（AFTER_COMMIT），`WorkOrderReleasedListener` 组装 `MesProductionOrderDTO`（orderNo 幂等）入 outbox | 已实现 |
| 3 | 工单进度回传 | MES→ERP 回传 | `POST /integration/mes/order-status`：仅更新 `mrp_work_order.mes_status` 镜像列，不改 ERP 状态机 | 已实现 |
| 4 | 完工报工回传 | MES→ERP 回传 | `POST /integration/mes/work-reports`：`StockService.stockIn(PROD_IN)` 产成品入库 + 回写 `finish_qty`/`bad_qty` 累计 + 状态达量 FINISHED（与 ERP 手工完工同口径） | 已实现 |
| 5 | 领退料回传 | MES→ERP 回传 | `POST /integration/mes/pickings`：材料出库 `stockOut(PROD_USE)`（带批次走 BATCH 精确扣，否则默认策略）；`POST /integration/mes/returns`：反向入库 `stockIn(OTH_IN)` 冲销 | 已实现 |
| 6 | 质检结果回传 | MES→ERP 回传 | `POST /integration/mes/quality-results`：结论合格 + FQC + 来源可关联工单 → 按合格量 PROD_IN 入库；不合格仅记录（实物由退料/红冲承担）。MES 侧约定报工与 FQC 二选一驱动入库，避免双计 | 最小实现 |

## 2. 时序

### 2.1 生产订单下推（事件驱动）

```
mrp release() ── 事务内 status=RELEASED + 工序任务生成
   └─ publishEvent(WorkOrderReleasedEvent)          ← mrp 模块唯一新增一行
        └─ AFTER_COMMIT: WorkOrderReleasedListener
             └─ OutboxService.enqueue(PRODUCTION_ORDER, woId, dto)
                  ├─ INSERT int_outbox(PENDING, payload JSON)
                  └─ dispatch → MesClient.pushProductionOrder
                       stub: 记日志即 SUCCESS（mock 留痕）
                       http: POST {base-url}/integration/erp/orders (X-Integration-Token)
```

### 2.2 回传（入站幂等）

```
MES ErpPushService ── POST /api/integration/mes/**  (X-Integration-Token)
   └─ MesInboundController.checkToken（静态配置令牌）
        └─ MesInboundService.accept(bizType, bizKey, eventTime)
             ├─ 已有 bizType+bizKey=SUCCESS → 留痕 DUPLICATE，直接返回成功
             ├─ INSERT int_message_log（uk: biz_type+biz_key+event_time 兜并发）
             ├─ 业务处理（同事务：库存过账/工单回写/状态镜像）
             └─ 回写结果摘要；任一步失败整体回滚不留脏日志
```

## 3. 可靠投递（outbox + 重试 + 死信）

- `int_outbox(bizType,bizId,eventTime,payload,status,retryCount,nextRetryTime,error)`；
- 失败指数退避 **1m/5m/30m/2h/6h**，5 次失败置 `DEAD` 死信；
- `OutboxRetryJob` 每分钟捞取 `PENDING && next_retry_time<=now`（fixedDelay 防重叠，每轮 ≤50 条）；
- 管理端 `POST /integration/outbox/{id}/retry` 手动重试（PENDING/DEAD 均可）；
- stub 模式客户端恒成功，outbox 即 SUCCESS，链路可先行联调。

## 4. 对账

主数据以 ERP 为事实源：每日全量重推即对账补偿，MES 按 `code`/`materialCode+version`/`routingCode` 幂等 upsert，差异随覆盖收敛。单据对账（状态+数量）当前由入站消息日志（含 DUPLICATE 留痕）承载追溯，差异补偿任务为后续迭代。

## 5. 配置（application.yml）

```yaml
erp:
  integration:
    mes:
      mode: stub                            # stub | http
      base-url: http://localhost:8081/api   # MES 开放接口基地址
      outbound-token: erp-integration-token # 下推令牌（= mes.integration.erp.inbound-token）
      inbound-token: erp-integration-token  # 回传校验令牌
      retry-enabled: true
      masterdata-reconcile-enabled: true
      masterdata-reconcile-cron: 0 0 2 * * ?
```

## 6. 认证与租户口径

- 入站 `/integration/mes/**` 无登录上下文：由本模块 `IntegrationSecurityConfig` 注册 `@Order(1)` 独立过滤链放行（零侵入共享 SecurityConfig），鉴权由 Controller 的 `X-Integration-Token` 承担；`@RequirePerm` 不适用。
- 租户插件对 `int_*`/`mrp_work_order` 查询按 `OrgContextHolder` 默认租户 1000 过滤——与现有定时任务（无上下文默认 1000/1）先例口径一致，入站链路无需特殊处理。

## 7. 管理端与前端

- `GET /integration/outbox`（分页/状态/类型筛选）、`POST /integration/outbox/{id}/retry`、`GET /integration/inbound-logs`、`POST /integration/masterdata/push`；
- 前端「集成监控」页 `erp-web/src/pages/integration/monitor/IntegrationMonitor.tsx`（outbox + 入站日志两 Tab），路由 `integration/monitor`，API `src/api/integration.ts`；
- 菜单：360 系统集成（目录）/ 361 集成监控（`integration:outbox:list`）+ 按钮权限码 `integration:outbox:retry` / `integration:log:list` / `integration:masterdata:push`（V1231 种子）。

## 8. SQL

`script/sql/migration/V1231__erp_mes_integration.sql`（版本段 V12xx 集成段）：
`int_outbox` / `int_message_log` 幂等建表 + `mrp_work_order.mes_status` 镜像列（information_schema 判断）+ 菜单种子（INSERT IGNORE，不写 org_id/tenant_id 列）。

## 9. mrp 挂点方式（最小侵入）

`MrpWorkOrderServiceImpl.doRelease` 末尾新增一行 `eventPublisher.publishEvent(new WorkOrderReleasedEvent(wo.getId()))`；事件类 `com.erp.mrp.event.WorkOrderReleasedEvent`（放 mrp 以保证依赖方向 integration→mrp）。未改任何既有方法签名。

## 10. 映射口径备注

- ERP 无 `material_type/weight/key_component` 列，物料下推对应字段置空；
- ERP 工艺路线无独立编码，`routingCode` 下推 `mrp_routing.version`（同物料版本唯一，可作幂等键）；
- 退料冲销使用 `OTH_IN`（库存业务类型枚举无"生产退料"专用值，流水单号挂退料单号可追溯）。
