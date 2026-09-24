# 采购管理模块（erp-purchase）设计说明

> 模块：erp-backend/erp-purchase + erp-web/src/pages/purchase
> SQL：script/sql/migration/V201__purchase_init.sql（版本段 V20x）
> 状态：开发完成，后端全链路（订单→审核→入库→退货→对账）curl 实测通过

## 1. 功能范围（对标金蝶采购管理）

| 功能 | 表 | 说明 |
|---|---|---|
| 采购订单 | pur_order / pur_order_detail | 草稿→已审核→部分入库→已完成/已关闭；仅草稿可改/删 |
| 采购入库单 | pur_instock / pur_instock_detail | 从订单选单生成，审核写库存+应付 |
| 采购退货单 | pur_return / pur_return_detail | 引用入库单退货，审核反向库存事务 |
| 供应商对账单 | （只读统计） | 按供应商+月汇总入库/退货/净额 |

## 2. 单据状态机（只允许正向流转）

- **采购订单**：`0草稿 → 10已审核 → 20部分入库 → 30已完成`；`10/20` 可人工关闭为 `90已关闭`（终态，不可再入库）。
  状态推进全部用**条件 UPDATE**（`WHERE status=...`）落地，并发重复提交时第二次更新影响 0 行直接报错，不会跳跃流转。
- **入库单/退货单**：`0草稿 → 10已审核`，同样条件 UPDATE 防重复审核。
- 例外（业务口径，与金蝶一致）：退货审核后订单"全部入库"不再成立时，允许 `30已完成 → 20部分入库` 回落（`PurOrderServiceImpl#refreshProgressStatus`）。

## 3. 单号规则

`前缀 + yyyyMMdd + 4位流水`，Redis INCR 生成（key=`erp:no:{前缀}:{日期}`，48h 过期）：

| 单据 | 前缀 | 示例 |
|---|---|---|
| 采购订单 | PO | PO202609050001 |
| 采购入库单 | RK | RK202609050001 |
| 采购退货单 | RT | RT202609050001 |

## 4. 金额/数量口径

- 明细金额 = 数量 × **含税**单价，服务端 `BigDecimal` HALF_UP 2 位计算（`DocCalc`），前端传入金额一律忽略；
- 主表价税合计 = 明细金额之和再收口 2 位；tax_rate 为百分数（如 13.00=13%），仅存储供财务换算不含税口径；
- 数量/单价 `DECIMAL(18,4)`，金额 `DECIMAL(18,2)`。

### 4.1 可入库/可退数量守卫（防草稿累计穿透）

`received_qty / returned_qty` 只在**审核**时回写，因此创建/修改单据时的可用量必须扣除
**草稿在途**，否则多张草稿单会累计超收/超退（已实测发现并修复）：

- 可入库量 = 订单数量 − 累计已入库 − 其他草稿入库单在途（`PurInstockDetailMapper#sumDraftPendingQty`）；
- 可退量 = 入库数量 − 累计已退 − 其他草稿退货单在途（`PurReturnDetailMapper#sumDraftPendingQty`）；
- 审核回写再加一道数据库兜底（并发竞态防御）：
  `addReceivedQtyGuarded`（`received_qty + qty <= 订单数量`）、
  `addReturnedQtyGuarded`（`returned_qty + qty <= 入库数量`），影响 0 行即整体回滚。

## 5. 跨模块写表时机（联调重点）

本模块【不创建】他人表；以下写入全部发生在**单据审核事务内**，任一步失败整体回滚：

| 目标表 | owner | 写入时机 | 操作 |
|---|---|---|---|
| inv_movement | inventory (V401) | 采购入库单审核 | 每明细 INSERT 一行：`biz_type='PUR_IN'`（采购入库）、`biz_id=入库单id`、`biz_no=入库单号`、`qty=+实收数量` |
| inv_stock | inventory (V401) | 采购入库单审核 | 行存在：`UPDATE inv_stock SET qty=qty+? WHERE id=? AND qty+?>=0`（行锁防并发+禁负库存）；行不存在：INSERT 新行（唯一键冲突回退 UPDATE） |
| inv_movement | inventory (V401) | 采购退货单审核 | 每明细 INSERT：`biz_type='PUR_RET'`、`qty=-退货数量` |
| inv_stock | inventory (V401) | 采购退货单审核 | 同上条件 UPDATE（负向），`qty+delta>=0` 不满足 → 抛业务异常整体回滚（**当前负库存策略=禁止**，如需放开改 `InventoryWriter#changeStock`） |
| fin_payable | finance (V501) | 采购入库单审核 | INSERT 一条：`supplier_id`、`biz_no=入库单号`、`amount=入库单价税合计`、`settled_amount=0`、`status='UNSETTLED'`（finance 字符串枚举）；结算/核销归 finance，本模块不 UPDATE |

涉及组件：`InventoryWriter`（本模块对 inv_* 的唯一写入口）、`FinPayableMapper`。

### 待联调确认项

1. **退货不生成负应付**：当前退货只做反向库存，不写 fin_payable 红字/负数行；红字方案（负金额行 or 冲减原单 settled_amount）需与 finance 模块对齐后补。
2. **fin_payable.biz_date** 目前留空（V501 注释"采购模块可空"）；如财务报表需要，可在入库审核时补 `biz_date=入库日期`。
3. **基础资料选项接口**：`/purchase/options/*` 是并行期的只读代理（直查 base_* 表）；basedata 标准接口稳定后前端可切换，口径一致。
4. **订单超收**：当前禁止超收（实收 ≤ 订单数量-累计已入库）；如需金蝶式超收比例，改 `PurInstockServiceImpl#buildDetails` 的 remain 校验。

## 6. base_* 只读引用（不 import basedata 代码）

关联查询一律走本模块 `BasedataMapper`（@Select 直查）+ XML JOIN，已按 V101 实际结构对齐：

- `base_material`：编码/名称/规格直接取；**计量单位是 `unit_id` 外键 → `base_unit.name`**（无 unit 列）；
- 供应商/物料/仓库校验与下拉均带 `status=1`（启用）过滤；单据展示 JOIN 不带 status（保留历史停用资料显示）。

## 7. API 清单（context-path=/api）

### 采购订单 /purchase/orders
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /purchase/orders | 分页：pageNum/pageSize/keyword(单号模糊)/supplierId/status/dateBegin/dateEnd |
| GET | /purchase/orders/{id} | 详情（主子表，含物料编码/名称/规格/单位） |
| POST | /purchase/orders | 新增（草稿），金额服务端计算 |
| PUT | /purchase/orders/{id} | 修改（仅草稿） |
| PUT | /purchase/orders/{id}/audit | 审核（草稿→已审核） |
| PUT | /purchase/orders/{id}/close | 关闭（已审核/部分入库→已关闭） |
| DELETE | /purchase/orders/{id} | 删除（仅草稿，逻辑删） |

### 采购入库 /purchase/instock
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /purchase/instock | 分页（keyword 匹配入库单号/订单号） |
| GET | /purchase/instock/{id} | 详情 |
| GET | /purchase/instock/from-order/{orderId} | 选单预填充（建议实收=订单数量-已入库，过滤剩余=0 的行） |
| POST | /purchase/instock | 新增（草稿，禁止超收） |
| PUT | /purchase/instock/{id} | 修改（仅草稿） |
| PUT | /purchase/instock/{id}/audit | 审核：库存流水→库存→应付→订单回写（一个事务） |
| DELETE | /purchase/instock/{id} | 删除（仅草稿，逻辑删） |

### 采购退货 /purchase/returns
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /purchase/returns | 分页（keyword 匹配退货单号/入库单号） |
| GET | /purchase/returns/{id} | 详情 |
| GET | /purchase/returns/from-instock/{instockId} | 选单预填充（建议退货=入库数量-已退） |
| POST | /purchase/returns | 新增（草稿，退货 ≤ 可退数量） |
| PUT | /purchase/returns/{id} | 修改（仅草稿） |
| PUT | /purchase/returns/{id}/audit | 审核：反向库存流水→扣库存→回写入库明细/订单 |
| DELETE | /purchase/returns/{id} | 删除（仅草稿，逻辑删） |

### 对账与选项
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /purchase/statements?supplierId&month=yyyy-MM | 月度汇总：入库金额/退货金额/净额/单数（仅已审核，按 [月初,次月初) 区间） |
| GET | /purchase/options/suppliers ・ /materials ・ /warehouses | 单据填写下拉（并行期只读代理） |

## 8. 前端

- `src/api/purchase.ts`：类型与请求（与后端 VO 对齐）；
- `src/pages/purchase/orders/OrderList.tsx`：列表（单号/供应商/状态/日期区间筛选）+ Drawer 详情（Descriptions+明细表+合计）+ Drawer 新建/修改（Form.List 明细行、金额实时预览、审核/关闭/删除操作）；
- `src/pages/purchase/instock/InstockList.tsx`：列表 + 选单生成（选已审核/部分入库订单→预填→改实收数量，上限=剩余可入库）+ 详情；
- `src/pages/purchase/returns/ReturnList.tsx`：列表 + 选单退货（选已审核入库单→预填→改退货数量）+ 详情；
- `src/pages/purchase/shared.tsx`：状态 Tag 映射（订单 5 态/单据 2 态）、基础资料选项 hooks、金额/数量格式化；
- `src/router/modules/purchase.tsx`：`purchaseRoutes`（purchase/orders、purchase/instock、purchase/returns，path 无首斜杠），菜单已在 MainLayout 预置。

## 9. 集成已接线（V2101，2026-09-05 主线程集成）

并行期的裸 SQL 挂点已全部切换为同 JVM Service 直调（依赖方向单向
purchase → {inventory, cost, qc, workflow}），`InventoryWriter`/`PurInvStockMapper`/
`PurInvMovementMapper` 及本模块 inv_* 实体已删除：

| 挂点 | 接线 | 说明 |
|---|---|---|
| 入库审核·库存 | `StockService.stockIn`（明细 batchNo 透传，V2101 加列） | 批次物料落批次行（缺批次号由 StockService 拒绝），非批次走汇总行；防负库存/流水不变量不变 |
| 入库审核·成本 | `CostService.onStockIn(mat, wh, "PUR_IN", 单号, 明细ID, qty, 含税金额, 入库日期)` | cost_flow 实时流水，无需 sync 重算 |
| 入库审核·质检 | `QcInspectService.pendingOfSource("pur_instock", id)` 前置卡点 | 存在草稿检验单→拒绝（提示"存在未完成检验单(单号)"）；无检验单→免检直过 |
| 退货审核·库存 | `StockService.stockOut`：入库明细带批次→BATCH 从原批次退回；否则 FIFO/汇总行 | 库存不足整体回滚（禁止负库存语义不变） |
| 退货审核·成本 | `CostService.onStockOut("PUR_RET")` | 按当前移动加权价冲减 |
| 订单审核·审批流 | `WorkflowService.startFlow("pur_order")` 三段式 | SKIPPED/AGREED 直接过；RUNNING 不执行返回"已提交审批"（单据保持草稿，`wf_started`=1）；审批通过后再审→执行原审核；驳回后再审→重新提交；被驳回/审批中的草稿允许关闭（close 条件已扩展） |
| 审核人 | `SecurityUtils.getCurrentUserId()` | audit_by 不再写 0 占位 |

迁移脚本：`script/sql/migration/V2101__sales_actual_cost.sql`（pur_instock_detail.batch_no、
pur_order.wf_started）。应付（fin_payable）仍按约定表直写（结算归 finance）。

## 10. 已知事项

- ~~审核人 `audit_by` 当前写 0（系统占位）~~ → V2101 已切换 `SecurityUtils.getCurrentUserId()`。
- 入库单审核后订单自动推进状态（全部入库→30，否则→20）；关闭的订单不能再生成入库单。
- 退货不生成负应付（红字方案仍待与 finance 联调）；出库成本过账依赖 erp-cost 台账
  （结存不足会被拒，属"账实不符禁止出库"口径）。

## 11. 采购前端链路（V2111：请购单 → 询比价 → 下推采购订单）

> SQL：`script/sql/migration/V2111__purchase_requisition_rfq.sql`（版本段 V211x）
> 后端：`erp-biz/erp-biz-purchase` 新增 PurRequisition/PurRfq 两组 Controller/Service/Entity/Mapper
> 前端：`pages/purchase/requisitions/RequisitionList.tsx`、`pages/purchase/rfqs/RfqList.tsx`

### 11.1 链路总览

```
请购单 pur_requisition            询比价 pur_rfq                     采购订单 pur_order
 0草稿 ──提交──> 10已提交 ──生成RFQ──> 0询价中 ──定标──> 10已定标 ──下推──> 订单草稿(0)
   │                │                  │  (录报价/比价矩阵)    │              (走既有 PurOrderService)
   └─ 关闭 → 30已关闭 └─ 关闭 → 30     └─ 取消 → 30已取消      └─ 回写请购明细 ordered_qty
                                     (终态)                 (pur_rfq_item.pur_order_id 仅一次)
```

### 11.2 表与状态机

| 表 | 说明 | 状态 |
|---|---|---|
| pur_requisition | 请购单主表（部门 sys_dept/请购人/期望日期） | 0草稿→10已提交→20已转采购；10/20→30已关闭 |
| pur_requisition_item | 请购明细（物料/数量/已转采购数量/需求日期） | — |
| pur_rfq | 询比价主表（可空关联请购单，截止日期） | 0询价中→10已定标；0→30已取消 |
| pur_rfq_supplier | 邀请供应商（报价金额合计，uk: rfq+supplier） | 0已邀请/10已报价/20中标 |
| pur_rfq_item | 询价明细（定标供应商/中标价/交期、下推订单 ID） | — |
| pur_rfq_quote | 报价行（rfq+供应商+明细 唯一，重录=覆盖） | — |

单号：请购 `PR+yyyyMMdd+4位流水`、询比价 `IN+yyyyMMdd+4位流水`（统一 OrderNoGenerator / Redis INCR）。

### 11.3 关键守卫（防超转/防篡改）

- **已转数量双层守卫**：RFQ 创建时应用层校验 `询价数量 <= 请购数量 - 已转数量`；
  定标时 `addOrderedQtyGuarded` 条件 UPDATE（`ordered_qty + delta <= qty`，影响 0 行整体回滚），
  多张 RFQ 并发定标也不会累计超转（超量直接报错）。
- **已定标禁改**：修改/录报价/删除仅限 0询价中；询价明细创建后不可改（防与报价错位），
  修改接口仅允许调整截止日期/备注/邀请名单（移出的供应商其报价一并删除）。
- **下推仅一次**：`pur_rfq_item.pur_order_id` 非空即拒绝再次下推；
  定标必须覆盖全部明细且中标供应商已报价（中标价/交期取自报价行）。
- **邀请 >=3 家供应商**：创建/修改时校验（三家起报，比价才有意义）。

### 11.4 比价矩阵与下推

- `GET /purchase/rfqs/{id}/comparison`：物料×供应商矩阵（报价单价/交期/总价=数量×单价 HALF_UP 2 位），
  每物料标记最低价（并列都标记），前端高亮辅助分项定标。
- `POST /purchase/rfqs/{id}/to-orders`：按定标供应商分组组装订单草稿
  （物料/数量=定标量/单价=中标价/交货日期=下推日+中标交期），**调既有 `PurOrderService.create`**
  （单号/金额/校验口径与手工建单一致），一供应商一单，remark 记「询比价 IN 单号 生成」；
  整个动作一个事务，任一订单失败全部回滚；RFQ 明细逐行回写 pur_order_id。

### 11.5 API 清单（context-path=/api）

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | /purchase/requisitions | requisition:list | 分页（单号模糊/状态/部门/物料） |
| GET | /purchase/requisitions/{id} | requisition:list | 详情（明细含已转数量） |
| GET | /purchase/requisitions/dept-options | 登录即可 | 请购部门下拉（sys_dept 只读） |
| POST | /purchase/requisitions | requisition:add | 新增（草稿） |
| PUT | /purchase/requisitions/{id} | requisition:edit | 修改（仅草稿） |
| PUT | /purchase/requisitions/{id}/submit | requisition:audit | 提交（0→10） |
| PUT | /purchase/requisitions/{id}/close | requisition:close | 关闭（10/20→30） |
| DELETE | /purchase/requisitions/{id} | requisition:delete | 删除（仅草稿） |
| GET | /purchase/rfqs | rfq:list | 分页（单号模糊/状态/来源请购单/供应商） |
| GET | /purchase/rfqs/{id} | rfq:list | 详情（供应商+明细含定标/下推回写） |
| GET | /purchase/rfqs/{id}/comparison | rfq:list | 比价矩阵（物料×供应商+最低价标记） |
| POST | /purchase/rfqs | rfq:add | 新增（可关联请购带入明细，>=3 家供应商） |
| PUT | /purchase/rfqs/{id} | rfq:edit | 修改（仅询价中：截止/备注/邀请名单） |
| POST | /purchase/rfqs/{id}/quotes | rfq:edit | 录供应商报价（同供应商重录=覆盖） |
| POST | /purchase/rfqs/{id}/award | rfq:award | 分项定标（回写请购已转数量） |
| POST | /purchase/rfqs/{id}/to-orders | rfq:toOrder | 下推采购订单草稿（一供应商一单） |
| PUT | /purchase/rfqs/{id}/cancel | rfq:edit | 取消（仅询价中） |
| DELETE | /purchase/rfqs/{id} | rfq:delete | 删除（仅询价中） |

菜单/按钮权限：sys_menu 215（请购管理）+2151~2155、216（询比价）+2161~2165，挂采购管理目录(210)下（V2111）。

### 11.6 前端

- `RequisitionList.tsx`：列表（状态/部门/物料筛选）+ 详情（明细含已转/剩余可转）+
  新建/修改（Form.List 明细行）+ 提交/关闭 + 「生成询价」跳转 `/purchase/rfqs?from={id}` 预填。
- `RfqList.tsx`：列表 + 新建（来源请购单带入余量明细、多选供应商）+ 报价录入 Drawer
  （选供应商→逐行单价/交期，回显已录报价）+ 比价矩阵 Drawer（动态供应商列、最低价高亮）
  + 分项定标 Modal（默认最低价、可改选）+ 下推订单（Popconfirm，提示生成订单号）。
- 路由/菜单：`router/modules/purchase.tsx` 追加 purchase/requisitions、purchase/rfqs
  （MainLayout 菜单与 locales 由主线程预置）。

## 12. 暂估应付（货到票未到，V231，2026-09-06）

> SQL：`script/sql/migration/V231__purchase_estimation.sql`（版本段 V23x）
> 后端：`erp-biz-purchase` 新增 PurEstimation 一组 Controller/Service/Entity/Mapper
> + `PurEstimationScheduleConfig` 扫描器 + `PurInstockAuditedEvent` 事件挂点；
> 前端：`pages/purchase/estimations/EstimationList.tsx`（路径 purchase/estimations）。

### 12.1 口径（对标金蝶暂估入库）

| 项 | 口径 |
|---|---|
| 暂估金额 | Σ 入库明细 `qty × 含税单价 ÷ (1+税率%)`——**不含税净额**，逐行 HALF_UP 2 位后合计（税率 0 取含税额） |
| 暂估条件 | 入库单已审核 + **无生效蓝字进项发票**（inv_purchase_invoice 待认证/已认证且未红冲） |
| 暂估单号 | `ZG + yyyyMMdd + 3位流水`（Redis INCR，OrderNoGenerator 新增宽度参数重载） |
| 暂估期间 | `period = 入库月份 yyyyMM`（月初补提上月单据时仍归属上月期间） |
| 发票实际金额 | **价税合计**（应付口径）：自动冲回取 inv_purchase_invoice.total_amount，手动冲回由表单传入 |
| 本次冲回 | `cover = min(发票实际金额, 未冲回余额)`；cover<余额 → 部分冲回(20)，=余额 → 已冲回(10) |
| 状态机 | `0暂估中 → 10已冲回(终态)/20部分冲回`；10 拒绝再冲回；同一发票号重复冲回被拒（reversed_invoice_nos 清单比对） |

fin_payable 联动（约定表只 INSERT，不改不删，结算/核销归 finance）：

| 时机 | 行 | biz_no | amount | remark 标记 |
|---|---|---|---|---|
| 暂估生成 | 暂估行 | ZG 单号 | +暂估金额（不含税） | 暂估应付（入库单 RK…，货到票未到） |
| 票到冲回 | 红字行 | ZG 单号 | -cover | 暂估红冲：发票 xxx |
| 票到冲回 | 正式行 | RK 入库单号 | +发票实际金额 | 票到入账：发票 xxx |
| 票到冲回 | 差额行（实际≠cover 时） | ZG 单号 | 实际-cover（可负） | 暂估差额调整：暂估/冲回/实际三值 |

> 差额行语义：暂估按不含税净额、发票按价税合计入账，两者的口径差（价差+税差）以独立行
> 显式留痕，便于对账追溯；供应商应付净额以正式行为准。
> **与并行任务凭证引擎的边界**：fin_payable 中 `biz_no` 前缀 `ZG` 的行即暂估链路产生
> （暂估/红冲/差额），凭证引擎按此前缀识别来源即可，两侧互不冲突；正式行 biz_no 为 RK 单号，
> 与入库审核产生的原应付同源可对账。所有行 status 沿用 finance 现有枚举 `UNSETTLED`。

### 12.2 开关与模式

| 配置 | 默认 | 说明 |
|---|---|---|
| `purchase.estimation.enabled` | true | 总开关：false 时事件挂点与扫描器均跳过（不再自动暂估/自动冲回，手工接口仍可用） |
| `purchase.estimation.mode` | instant | `instant` 审核即暂估（AFTER_COMMIT 事件，失败不影响入库审核事务）；`month-end` 月末汇总——扫描器只补提已关账月份（入库日期<当月 1 号，即"月初暂估"） |

扫描器（`PurEstimationScheduleConfig`，60s 一轮，逐单独立事务+吞异常）：
①补提漏单暂估（instant 补全部、month-end 只补关账月）；②已认证蓝字发票自动冲回。

### 12.3 联动图

```
入库审核(PurInstockService.audit, 原事务不变)
   └─发布 PurInstockAuditedEvent ──AFTER_COMMIT──> 暂估服务
                                                  ├─ instant且无生效发票 → INSERT pur_estimation
                                                  │   + fin_payable(ZG 行, +暂估额)
                                                  └─ 失败→扫描器兜底
进项发票认证(inv 模块, status 0→10)
   └─扫描器(60s) ──> 关联入库单存在未冲完暂估且该发票未用过 → 冲回：
       fin_payable 红字(-cover) + 正式(+发票价税合计) + 差额行(≠0 时)
       pur_estimation: reversed_amount+=cover, status 10/20
手工补救：POST /purchase/estimations/from-instock/{instockId}
          POST /purchase/estimations/{id}/reverse {invoiceNo, actualAmount}
```

### 12.4 API（权限码 purchase:estimation:{list,edit,audit}）

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | /purchase/estimations | list | 分页：keyword(暂估/入库/发票号)/supplierId/status/period |
| GET | /purchase/estimations/summary?month=yyyyMM | list | 报表：当月暂估总额/已冲回/未冲回余额+供应商维度行（month 缺省当月） |
| POST | /purchase/estimations/from-instock/{instockId} | edit | 手工生成（已审核+无暂估+无生效发票） |
| POST | /purchase/estimations/{id}/reverse | audit | 手动冲回 {invoiceNo, actualAmount=价税合计} |

菜单：sys_menu 217（暂估应付，挂采购管理 210）+按钮 2171(edit)/2172(audit)（V231）；
前端路由 `purchase/estimations` 已追加进 `router/modules/purchase.tsx`（MainLayout 菜单项与
locales 由菜单协调者统一挂载）。

### 12.5 守卫与并发

- 一入库单一暂估：uk(instock_id, deleted) + 代码预检（DuplicateKeyException 转业务错）；
- 冲回进度条件 UPDATE：`status IN (0,20) AND amount - reversed_amount >= cover`，
  数据库层防并发超冲/重复冲回，影响 0 行整体回滚；
- 事件挂点 AFTER_COMMIT + 自管事务：暂估失败不回滚入库审核（库存/应付/订单回写原子性优先）；
- 已全额冲回(10)再冲回、同一发票重复冲回、金额<=0 一律业务拒绝。
