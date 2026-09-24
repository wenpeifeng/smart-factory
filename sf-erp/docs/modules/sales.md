# 销售管理模块（erp-sales）设计文档

> 版本：v1.1（2026-09-05） · 对标金蝶 K3 销售管理
> 范围：销售报价单 / 发货通知单（V3111 链路深化）+ 销售订单 / 销售出库 / 销售退货 / 销售统计

## 1. 模块定位与单据流

```
销售报价单(sal_quotation) ──提交报价──→ 已报价 ──下推──→ 销售订单(草稿)
                                                        │
发货通知单(sal_delivery_notice) ←──选单(按订单余量)──────┤ 已审核订单
      │ 通知 → 已通知 → 按出库单号核销 → 已出库          │ 选单生成 ↓
      └ 备货参考(pending-stocks: 物料×仓库可用量)         销售出库单(sal_outstock) ──审核──→ 扣减 inv_stock / 写 inv_movement(负) / 生成 fin_receivable(蓝字)
                                                         │ 引用退货 ↓
                                                         销售退货单(sal_return) ──审核──→ 回补 inv_stock / 写 inv_movement(正) / 生成 fin_receivable(红字)
                                                                                                回写出库行 returned_qty
```

## 2. 数据表

### 2.1 自有表（V301~V303，V3111）

| 表 | 说明 | 关键列 |
|---|---|---|
| `sal_quotation` | 报价单头（V3111） | quotation_no(uk, BJ 前缀)、customer_id、quote_date、valid_until、discount_rate%(可空)、status、order_id/order_no(下推回写) |
| `sal_quotation_item` | 报价明细行（V3111） | quotation_id、material_id、qty、price、cost_price(ref_cost 快照,只读带回)、amount=qty×price×(1−折扣) |
| `sal_order` | 销售订单头 | order_no(uk, SO+yyyyMMdd+4位流水)、customer_id、warehouse_id、total_qty/total_amount、status、audit_by/audit_time、close_by/close_time |
| `sal_order_detail` | 订单明细行 | order_id、material_id、qty、price、amount、out_qty(出库回写) |
| `sal_delivery_notice` | 发货通知单头（V3111） | notice_no(uk, FH 前缀)、order_id/order_no(可空=直发)、customer_id、warehouse_id、plan_date、status |
| `sal_delivery_notice_item` | 通知明细行（V3111） | notice_id、material_id、qty(通知数量)、out_qty(核销累加)、order_detail_id(可空) |
| `sal_outstock` | 出库单头 | out_no(uk, XS 前缀)、order_id/order_no、customer_id、warehouse_id、status |
| `sal_outstock_detail` | 出库明细行 | out_id、order_detail_id、material_id、qty、price、amount、returned_qty(退货回写) |
| `sal_return` | 退货单头 | return_no(uk, RT 前缀)、out_id/out_no、customer_id、warehouse_id、status |
| `sal_return_detail` | 退货明细行 | return_id、out_detail_id、material_id、qty、price、amount |

通用约定：所有表带 id/create_by/create_time/update_by/update_time/deleted（逻辑删除）；
金额 DECIMAL(18,2)、数量 DECIMAL(18,4)；行金额 = qty×price 四舍五入 2 位（HALF_UP）。

### 2.2 跨模块表（只按约定结构读写，不 import 对方代码）

| 表 | 归属(建表) | 销售模块读取时机 | 销售模块写入时机 |
|---|---|---|---|
| `base_customer` | basedata V101 | 订单新增/审核：校验客户存在；读 credit_limit | 不写 |
| `base_warehouse` | basedata V101 | 订单新增：校验仓库存在 | 不写 |
| `base_material` | basedata V101 | 分页/详情/统计：JOIN 带出编码/名称/规格；统计取 ref_cost 作成本 | 不写 |
| `inv_stock` | inventory V401 | — | 出库审核：条件扣减 `UPDATE inv_stock SET qty=qty-?, version=version+1 WHERE warehouse_id=? AND material_id=? AND deleted=0 AND qty-?>=0`（影响行数=0 即库存不足，事务回滚）；退货审核：`INSERT ... ON DUPLICATE KEY UPDATE qty=qty+?` 回补 |
| `inv_movement` | inventory V401 | — | 出库审核：逐行写 **负数** 流量（biz_type=`SAL_OUT`，biz_id=出库单ID，biz_no=出库单号）；退货审核：逐行写 **正数** 流水（biz_type=`SAL_RET`） |
| `fin_receivable` | finance V501 | 订单审核：`SUM(amount - settled_amount)` 汇总客户未收回应收（余额） | 出库审核：INSERT 蓝字应收（amount=正，settled_amount=0，status='UNSETTLED'，biz_no=出库单号，biz_date=出库日期）；退货审核：INSERT **红字** 应收（amount=负数冲减余额，status='UNSETTLED'，biz_no=退货单号） |

> 红字应收说明：退货不以负数数量表述，金额取负值（-退货金额），使财务侧
> `SUM(amount - settled_amount)` 的客户应收余额自然冲减；核销逻辑由 finance 模块负责。

## 3. 状态机

### 3.1 销售订单 status（TINYINT）

```
0 草稿 ──审核──→ 10 已审核 ──出库回写──→ 20 部分出库 ──全部出库──→ 30 已完成（终态）
                    │ 20/10 ──关闭──→ 90 已关闭（终态，剩余数量不再执行）
0 草稿：可修改/可删除（逻辑删除）
```

- 审核前置校验：**信用额度**：`credit_limit − SUM(未收回应收) − 本单金额 ≥ 0`，否则拒绝
  （credit_limit 为 NULL 视为不控制；未收回应收按 fin_receivable 金额余额汇总，红字自然冲减）；
- 所有流转用条件 UPDATE（WHERE 携带旧状态）防并发重复审核/跳跃流转；
- 订单状态由出库单审核后自动刷新（部分出库/已完成），无需人工干预。

### 3.2 出库单/退货单 status

```
0 草稿 ──审核──→ 10 已审核（执行单据，不可再改/删）
```

### 3.3 报价单 status（V3111，TINYINT）

```
0 草稿 ──提交报价──→ 10 已报价 ──下推──→ 20 已转订单（终态）
0/10 ──作废──→ 40 已作废（终态）
10 且 valid_until < 当天 = 30 已过期（查询时动态判定，不落库；过期报价下推被拒）
```

- 有效期含当天（当天内仍可下推）；分页/详情返回"有效状态"（已过期显示 30）+ 落库 rawStatus；
- 毛利率口径：`(报价单价 − 成本参考) / 报价单价 × 100`（按报价原价口径），警戒线 20%；
  成本参考 = 录入时 base_material.ref_cost 快照（明细 cost_price 列，只读带回，不接受前端回传）。

### 3.4 发货通知单 status（V3111，TINYINT）

```
0 草稿 ──通知──→ 10 已通知 ──全部明细核销到位──→ 20 已出库（终态）
0/10（且未发生任何出库）──取消──→ 30 已取消（终态）
```

- 来源双通道：从已审核/部分出库订单选单（守卫"累计通知 ≤ 订单行剩余 qty−out_qty"，
  客户/仓库强制取订单值）或无来源直发（客户/仓库/物料手工指定）；
- 已出库数量只由核销接口累加（条件 UPDATE 守卫 out_qty+δ≤qty），超量整单拒绝回滚。

## 4. 关键业务规则

1. **单号**：`前缀 + yyyyMMdd + 4位流水`（Redis INCR，key=`erp:seq:{前缀}:{日期}`，2 天过期）。
   前缀：SO 订单 / XS 出库 / RT 退货。Redis 故障时降级为"当日已有单据数+1"，uk 索引兜底防重。
2. **出库数量约束**：出库行数量 ≤ 订单行剩余数量（qty − out_qty）；创建时校验 + 审核时
   条件 UPDATE（`out_qty + ? ≤ qty`）双保险，防并发超发。
3. **退货数量约束**：退货行数量 ≤ 出库行可退数量（qty − returned_qty），同为创建校验 + 审核条件更新双保险。
4. **出库审核事务**（`@Transactional(rollbackFor = Exception.class)`，任一步失败整体回滚）：
   1) 条件流转状态 0→10；2) 逐行扣 inv_stock（0 行=库存不足，抛 BusinessException）；3) 逐行写 inv_movement 负数；
   4) INSERT fin_receivable 蓝字；5) 回写订单行 out_qty 并刷新订单状态。
5. **金额全链 BigDecimal**，行金额 HALF_UP 2 位后汇总；数量 4 位小数。
6. 修改/删除仅限草稿；出库单、退货单不支持修改（删草稿重建）。

## 5. REST API（context-path /api）

### 5.1 销售订单 /sales/orders

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/sales/orders?pageNum&pageSize&keyword&status` | 分页（keyword 匹配订单号/客户名称，JOIN 带出客户/仓库名称） |
| GET | `/sales/orders/{id}` | 详情（表头+明细，明细含 remainingQty 剩余可出） |
| POST | `/sales/orders` | 新增（草稿）body：SalOrderForm |
| PUT | `/sales/orders/{id}` | 修改（仅草稿，明细整体替换） |
| PUT | `/sales/orders/{id}/audit` | 审核（信用额度校验） |
| PUT | `/sales/orders/{id}/close` | 关闭（已审核/部分出库） |
| DELETE | `/sales/orders/{id}` | 删除（仅草稿，逻辑删除） |

### 5.2 销售出库 /sales/outstock

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/sales/outstock?pageNum&pageSize&keyword&status` | 分页 |
| GET | `/sales/outstock/{id}` | 详情（明细含 returnableQty 可退） |
| POST | `/sales/outstock` | 从订单选单生成 body：{orderId, outDate, remark, details:[{orderDetailId, qty}]} |
| PUT | `/sales/outstock/{id}/audit` | 审核（扣库存/写流水/生应收/回写订单） |
| DELETE | `/sales/outstock/{id}` | 删除（仅草稿） |

### 5.3 销售退货 /sales/returns

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/sales/returns?pageNum&pageSize&keyword&status` | 分页 |
| GET | `/sales/returns/{id}` | 详情 |
| POST | `/sales/returns` | 引用出库单退货 body：{outId, returnDate, remark, details:[{outDetailId, qty}]} |
| PUT | `/sales/returns/{id}/audit` | 审核（回补库存/写流水/生红字应收） |
| DELETE | `/sales/returns/{id}` | 删除（仅草稿） |

### 5.4 销售统计 /sales/statistics

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/sales/statistics/daily?from&to` | 按日汇总销售额/成本/毛利（成本=base_material.ref_cost×数量；仅已审核出库单） |
| GET | `/sales/statistics/rank?type=material\|customer&from&to` | TOP10 排行（from/to 可选，默认不限） |

### 5.5 销售报价单 /sales/quotations（V3111）

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/sales/quotations?pageNum&pageSize&keyword&customerId&status&validFrom&validTo` | sales:quotation:list | 分页（status=30 查已过期；有效期区间筛选） |
| GET | `/sales/quotations/{id}` | sales:quotation:list | 详情（表头+明细，成本参考快照带回） |
| POST | `/sales/quotations` | sales:quotation:add | 新增（草稿）body：SalQuotationForm |
| PUT | `/sales/quotations/{id}` | sales:quotation:edit | 修改（仅草稿，明细整体替换，成本快照重取） |
| DELETE | `/sales/quotations/{id}` | sales:quotation:delete | 删除（仅草稿，逻辑删除） |
| PUT | `/sales/quotations/{id}/submit` | sales:quotation:audit | 提交报价 0→10 |
| PUT | `/sales/quotations/{id}/void` | sales:quotation:audit | 作废 0/10→40 |
| GET | `/sales/quotations/{id}/margin` | sales:quotation:list | 行毛利率视图（warn=低于 20% 警戒） |
| POST | `/sales/quotations/{id}/to-order` | sales:quotation:toOrder | 下推销售订单草稿（校验已报价且未过期；单价=报价×(1−折扣)，remark 记"报价单生成：BJxxx"；body 可选 {warehouseId, orderDate}，仓库缺省取第一个在用仓库），返回订单 ID |

### 5.6 发货通知单 /sales/delivery-notices（V3111）

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/sales/delivery-notices?pageNum&pageSize&keyword&status` | sales:deliveryNotice:list | 分页（含通知/已出库数量汇总） |
| GET | `/sales/delivery-notices/pending-stocks?warehouseId&keyword` | sales:deliveryNotice:list | 备货参考：物料×仓库可用量（SUM inv_stock.qty） |
| GET | `/sales/delivery-notices/{id}` | sales:deliveryNotice:list | 详情（明细含已出库/剩余数量） |
| POST | `/sales/delivery-notices` | sales:deliveryNotice:add | 新增（从订单选单 body：{orderId, planDate, remark, details:[{orderDetailId, qty}]}；直发 body：{customerId, warehouseId, planDate, remark, details:[{materialId, qty}]}） |
| PUT | `/sales/delivery-notices/{id}` | sales:deliveryNotice:edit | 修改（仅草稿） |
| DELETE | `/sales/delivery-notices/{id}` | sales:deliveryNotice:delete | 删除（仅草稿） |
| PUT | `/sales/delivery-notices/{id}/notify` | sales:deliveryNotice:audit | 通知发货 0→10 |
| PUT | `/sales/delivery-notices/{id}/cancel` | sales:deliveryNotice:cancel | 取消（仅未发生出库） |
| POST | `/sales/delivery-notices/{id}/settle` | sales:deliveryNotice:settle | 按出库单号核销：body {outstockNo}（须已审核出库单；守卫不超通知量） |

## 6. 前端

- 页面：`src/pages/sales/orders/OrderList.tsx`、`outstock/OutstockList.tsx`、`returns/ReturnList.tsx`
  （列表 + Drawer 详情 + 状态 Tag + 选单/编辑 Modal，风格对齐 UserList 模板）；
  V3111 追加：`quotations/QuotationList.tsx`（明细 Drawer 含毛利率红色警戒列 + 转订单 Modal）、
  `notices/NoticeList.tsx`（从订单生成/直发双模式 Modal + 备货参考 Drawer + 核销 Modal），
  两页采用统一规范组件（PageHeader/PageTable/StatusTag/useSubmitting/HasPerm/useDebounce）；
- API：`src/api/sales.ts`；
- 路由：`src/router/modules/sales.tsx` 导出 `salesRoutes`（path 无首斜杠：
  `sales/orders`、`sales/outstock`、`sales/returns`、`sales/quotations`、`sales/notices`），
  由主线程合并进 router/index.tsx；菜单路径（/sales/orders 等）已预置在 MainLayout。

## 7. 集成已接线（V2101，2026-09-05 主线程集成）

并行期的裸 SQL 挂点已全部切换为同 JVM Service 直调（依赖方向单向
sales → {inventory, cost, workflow}），`SalesBizMapper` 的
deductStock/restock/insertMovement 已删除，fin_receivable 直写保留：

| 挂点 | 接线 | 说明 |
|---|---|---|
| 出库审核·库存 | `StockService.stockOut(FIFO)` | 批次物料按 first_in_at 拆批扣减（跨批自动拆多行 movement，同一 bizNo）；非批次走汇总行原逻辑；不足整体回滚 |
| 出库审核·成本 | `CostService.onStockOut(mat, wh, "SAL_OUT", 单号, 出库单ID, qty, 出库日期)`，返回值回写 `sal_outstock_detail.actual_cost`（V2101 加列） | 实际成本实时归集；统计毛利升级 |
| 退货审核·库存 | `StockService.stockIn`（明细 batchNo 透传，V2101 加列） | 批次物料回补指定批次行（缺批次号拒绝），非批次汇总行 |
| 退货审核·成本 | `CostService.onStockIn("SAL_RET", amount=null)` | 按当前移动加权价回冲 |
| 订单审核·审批流 | `WorkflowService.startFlow("sal_order")` 三段式 | SKIPPED/AGREED 直过；RUNNING 返回"已提交审批"（`wf_started`=1）；审批通过后再审→执行原审核（信用校验在前）；驳回后可重提或关闭作废（close 条件已扩展） |
| 审核人 | `SecurityUtils.getCurrentUserId()` | audit_by/close_by 不再写 0 占位 |
| 统计口径 | `selectDailyStats`：`actual_cost` 优先，NULL（历史行）回退 `base_material.ref_cost` | 出库明细 VO 增带 actualCost 字段 |

迁移脚本：`script/sql/migration/V2101__sales_actual_cost.sql`（sal_outstock_detail.actual_cost、
sal_return_detail.batch_no、sal_order.wf_started）。

## 8. 已知边界与协作事项

1. **跨模块表结构**以 V401（inventory）/V501（finance）真实 DDL 为准；销售模块 SQL 只用约定列
   （biz_type 用 `SAL_OUT`/`SAL_RET`，应收 status 用 `UNSETTLED`，红字以负金额表述）。
2. ~~审核人 audit_by 当前传 0（系统）~~ → V2101 已切换 `SecurityUtils.getCurrentUserId()`。
3. 统计口径为"出库毛额"，退货不冲减销售额；净额/账龄口径由 finance 模块报表负责。
4. 信用校验只看 fin_receivable 余额（amount − settled_amount），不区分 status；
   若 finance 后续引入"作废"状态（如 VOID），需要销售侧同步排除，属联动改造点。
5. 出库单/退货单无"反审核/冲销"功能（对标金蝶以红字退货单实现冲销）。
6. 出库指定批次（BATCH 策略）当前不提供表单入口（统一 FIFO）；批次物料的指定批次出库
   走库存模块"其他出库单"。出库成本过账依赖 erp-cost 台账（结存不足会被拒，
   属"账实不符禁止出库"口径）。

## 9. 报价 → 订单 → 发货通知链路（V3111，2026-09-05）

新增"销售前端链路深化"：报价单（BJ）与发货通知单（FH），补齐
报价 → 下推销售订单 → 发货通知 → 出库核销的前端业务闭环。

### 9.1 单据链路

```
报价单(0草稿→10已报价) ──POST /{id}/to-order──→ 销售订单(0草稿)
   │ 校验：已报价 且 未过期(valid_until≥当天)          │ 复用既有 SalOrderService.create（同事务）
   │ 单价 = 报价单价 × (1 − 整单折扣率/100)             ↓
   └ 回写：状态 20已转订单 + order_id/order_no      订单审核(10) ──选单(按行余量)──→ 发货通知单(0草稿)
                                                        │ 通知 → 10已通知
                                                        │ 出库单 remark 记 FH 单号（挂点约定）
                                                        ↓ 出库审核后 POST /{id}/settle {outstockNo}
                                                  核销明细 out_qty（守卫不超通知量）→ 20已出库
```

关键规则：
1. **报价折扣**：整单折扣率%（0-100，空=不打折）；行金额 = qty × price × (1 − 折扣率/100)；
   下推订单单价同口径（HALF_UP 2 位），订单备注固定"报价单生成：BJ 单号"；
2. **过期动态判定**：status=10 且 valid_until < 当天 → 有效状态 30（不落库）；
   下推条件更新 WHERE 同时携带 status=10 与 valid_until≥CURDATE()，防过期竞态；
3. **通知余量守卫**：从订单生成/修改时校验
   `Σ有效通知(0/10)数量 + 本次 ≤ 订单行 qty − out_qty`（编辑时排除自身）；
4. **核销匹配**：settle 按出库单号读已审核出库单，明细按（order_detail_id + material）分组；
   通知行有订单行引用时精确匹配，直发行按物料匹配；逐行条件 UPDATE 守卫
   `out_qty + δ ≤ qty`，任一行超量整单拒绝回滚；全部核销到位自动流转 20已出库；
5. **成本快照**：报价明细 cost_price 录入时取 base_material.ref_cost，
   不接受前端回传（防篡改/防调价失真）。

### 9.2 出库回写挂点约定（不修改既有出库代码）

发货通知单的"已出库数量"不侵入出库审核流程，采用**约定 + 核销**模式：

| 环节 | 约定 |
|---|---|
| 出库单创建 | 出库单 remark 记录发货通知单号（FH 前缀），作为人工对账线索（可选但推荐） |
| 出库单审核 | 既有流程不动（扣库存/写流水/生应收/回写订单） |
| 核销 | `POST /sales/delivery-notices/{id}/settle {outstockNo}`：按出库单号核销（当前手动；主线程后续可在出库审核事务内改为自动核销——出库明细已带 order_detail_id，可直接定位通知行） |
| 备货参考 | `GET /sales/delivery-notices/pending-stocks?warehouseId&keyword`：物料×仓库可用量（SUM inv_stock.qty，跨模块只读） |

### 9.3 权限码与菜单（V3111 种子，挂销售管理目录 220）

- `sales:quotation:{list,add,edit,delete,audit,toOrder}`（菜单 225 + 按钮 2251~2255）；
- `sales:deliveryNotice:{list,add,edit,delete,audit,cancel,settle}`（菜单 226 + 按钮 2261~2266）；
- 单号前缀：BJ 报价 / FH 通知（复用 SalesNoGenerator：前缀+yyyyMMdd+4位流水）。

### 9.4 已知边界

1. 通知行 order_detail_id 引用的订单行后续关闭/修改不级联（通知为软引用，
   出库侧仍有订单余量硬守卫兜底）；
2. settle 当前为手动核销（一个出库单可多次核销同一通知直至出完；重复核销同一出库单
   会因剩余不足被拒绝）；出库单与通知跨客户/跨仓库不校验（以物料匹配为准），
   上线策略可按需收紧；
3. 报价单无审批流（提交报价即生效）；如需接入 WorkflowService 参照订单三段式扩展。
