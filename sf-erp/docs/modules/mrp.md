# 生产制造模块（erp-mrp）

> 对标金蝶 K3 生产制造域的 MRPII 闭环：BOM → MPS → MRP 运算 → 建议（转生产/转采购）→ 生产订单 → 领料 → 完工入库，及简化版委外加工。
> 后端模块：`erp-backend/erp-mrp/`；前端：`erp-web/src/pages/mrp/`；迁移脚本：`script/sql/migration/V601__mrp_init.sql`（mrp 模块使用 V60x 段）。

## 1. 数据表（11 张，均为 mrp_ 前缀）

| 表 | 说明 | 关键字段 |
|---|---|---|
| mrp_bom | BOM 清单头 | material_id（成品）、version、status（DRAFT/ACTIVE/DISABLED） |
| mrp_bom_item | BOM 子件明细 | bom_id、material_id、qty DECIMAL(18,6) 用量、loss_rate 损耗率% |
| mrp_mps | 主生产计划 | material_id、qty、start_date、status（PENDING/CALCULATED） |
| mrp_calc | MRP 运算批次 | batch_no（MR+日期+流水，唯一）、mps_count、suggestion_count |
| mrp_calc_detail | 运算展开明细（全树） | batch_no、level、毛/净需求、库存/在途快照、node_path/parent_path |
| mrp_suggestion | 发补建议 | batch_no、plan_type（PURCHASE/PRODUCE）、qty、suggest_date、status、ref_id |
| mrp_work_order | 生产订单 | order_no（MO+日期+流水）、status 状态机、finish_qty/bad_qty |
| mrp_material_issue | 生产领料单头 | issue_no（MI+…）、work_order_id、warehouse_id、status |
| mrp_material_issue_item | 领料明细 | should_qty（BOM×订单数量×(1+损耗)）、actual_qty |
| mrp_outsource | 委外加工单 | order_no（OS+…）、supplier_id、fee_unit_price、status（DRAFT/ISSUED/RECEIVED） |
| mrp_outsource_item | 委外发料明细 | qty（BOM×委外数量×(1+损耗)） |

## 2. MRP 运算逻辑（MrpServiceImpl）

```
净需求 = 毛需求 − 现有库存(inv_stock 按物料全仓 SUM) − 在途(pur_order 已审核未入库)
子件毛需求 = 父件净需求 × BOM用量 × (1 + 损耗率/100)
建议日期 = 需求日期 − 提前期 7 天（逐级再减 7 天，base_material 无提前期字段，固定 7）
```

- **可用池扣抵**：同一运算批次内，物料的“库存+在途”作为共享池按出现顺序扣抵；多 MPS / 多分支重复出现同一物料不会重复抵扣。
- **建议类型判定**：物料存在有效(ACTIVE) BOM → PRODUCE（继续向下展开）；无 BOM → PURCHASE（采购件叶子）。成品自身缺口产 PRODUCE 建议（即可直接“转生产”）。
- **防环与深度**：BFS 展开携带祖先物料链，命中即抛“循环引用”异常；层级上限 15 层。
- **在途口径**：`pur_order.status IN (10已审核,20部分入库)` 且 `qty > received_qty` 的明细 SUM；采购模块表未上线时按 0 优雅降级。
- 结果三表落库（批次头/展开明细/建议），MPS 行标记 CALCULATED；全程 `@Transactional`。

## 3. 状态机

```
BOM:          DRAFT → ACTIVE → DISABLED
生产订单:      DRAFT → RELEASED → PRODUCING(首次领料审核) → FINISHED(完工累计达标) → CLOSED
领料单:        DRAFT → AUDITED（审核=条件UPDATE扣库存+写inv_movement PROD_USE）
委外单:        DRAFT → ISSUED(发料扣库存 OUT_USE) → RECEIVED(成品入库 OUT_IN + 写应付)
建议:          PENDING → CONVERTED（转生产订单回写 ref WORK_ORDER；转采购订单回写 ref PUR_ORDER）
```

## 4. 接口清单（/api 前缀，JWT 认证）

| 分组 | 接口 |
|---|---|
| BOM | GET /mrp/boms；GET /mrp/boms/{id}；POST /mrp/boms；PUT /mrp/boms/{id}；DELETE /mrp/boms/{id}；POST /mrp/boms/{id}/copy；POST /mrp/boms/{id}/audit；POST /mrp/boms/{id}/disable；**GET /mrp/boms/{id}/tree**（多级树形展开，防环+深度15） |
| MPS | GET /mrp/mps；POST /mrp/mps；PUT /mrp/mps/{id}；DELETE /mrp/mps/{id} |
| MRP 运算 | POST /mrp/calc/run；GET /mrp/calc/batches；GET /mrp/calc/batches/{batchNo}/details（全树） |
| 建议 | GET /mrp/suggestions（batchNo/planType/status 过滤）；POST /mrp/suggestions/{id}/to-work-order；**POST /mrp/suggestions/{id}/to-purchase（转采购，V612 接线）**；**POST /mrp/suggestions/to-purchase-batch（批量转采购）** |
| 生产订单 | GET/POST /mrp/work-orders；GET/PUT/DELETE /mrp/work-orders/{id}；POST /{id}/release；POST /{id}/finish；POST /{id}/close |
| 领料 | GET/POST /mrp/material-issues；GET/PUT/DELETE /mrp/material-issues/{id}；POST /{id}/audit |
| 委外 | GET/POST /mrp/outsources；GET/PUT/DELETE /mrp/outsources/{id}；POST /{id}/issue；POST /{id}/receive |
| 支撑 | GET /mrp/materials（物料选择器分页）；GET /mrp/warehouses；GET /mrp/suppliers |

前端：`mrp/bom`（BOM 列表+编辑+树形展开）、`mrp/mrp`（MPS 录入→运算→批次/建议类型 Tab/可折叠树明细）、`mrp/workorders`（生产订单+领料/完工+委外）；路由导出 `mrpRoutes`（src/router/modules/mrp.tsx）。

## 5. 跨模块集成契约（并行协作，直接 SQL 不 import 对方代码）

集成网关：`MrpIntegrationMapper` + `resources/mapper/mrp/MrpIntegrationMapper.xml`，**只使用以下约定列**：

| 表 | 使用的列 | 用途 |
|---|---|---|
| base_material | id, code, name, spec, unit_id→base_unit.name, status=1 | 物料回显/选择器 |
| base_warehouse / base_supplier | id, name | 仓库/供应商选择器 |
| inv_stock | warehouse_id, material_id, qty, version | 库存汇总；**扣减**：`UPDATE inv_stock SET qty=qty-? WHERE warehouse_id=? AND material_id=? AND qty>=?`（影响 0 行=不足抛 BusinessException）；**回写**：upsert（依赖 uk(warehouse_id,material_id,deleted)） |
| inv_movement | warehouse_id, material_id, biz_type, biz_id, biz_no, qty(正入负出), remark | 写流水：PROD_USE / PROD_IN / OUT_USE / OUT_IN |
| pur_order / pur_order_detail | o.status IN(10,20), d.qty, d.received_qty | MRP 采购在途 SUM |
| fin_payable | supplier_id, biz_no, amount, status='UNSETTLED', remark | 委外加工费应付 INSERT |

## 6. 集成点与遗留说明

1. **转采购（已接线，V612，2026-09-06）**：PURCHASE+PENDING 建议可转采购订单草稿——
   `POST /mrp/suggestions/{id}/to-purchase`（单条）与
   `POST /mrp/suggestions/to-purchase-batch {ids[],supplierId?,warehouseId?}`（同供应商合并一张单、
   每条建议一行明细），调采购模块 `PurOrderService.create`（erp-biz-mrp pom 增加
   erp-biz-purchase 依赖，方向 mrp→purchase 单向无循环）。草稿规则：供应商可空=待用户
   补填（V612 放开 pur_order.supplier_id 可空，采购侧审核前强校验非空）、单价 0 待补、
   要求交货日期=建议日期、备注「MRP 生成，批次号+建议ID」；成功后建议 CONVERTED 并回写
   refType=PUR_ORDER / refId=采购订单 ID，重复转单/非 PURCHASE 类型被拒。前端「转采购」
   按钮已接通（MrpPage + suggestionApi.toPurchase/toPurchaseBatch）。
2. **inv_movement.biz_type 扩展**：本模块新增 `OUT_USE`/`OUT_IN`（委外），库存模块枚举注释如需补充请同步。
3. **fin_payable 无 biz_type 列**：应付与委外单的关联通过 `biz_no = 委外单号(OS…)` 表达。
4. **BOM 审核/停用与版本**：同物料允许多个 ACTIVE 版本，MRP/订单取最新 ACTIVE（按 id 倒序）；生产订单下达时若快照版本已失效会自动刷新到最新有效版本。
5. **委外为简化模型**：一发料一入库，不支持分批；应付金额 = 入库良品数量 × 加工费单价。
6. 验证过程中发现并推动修复的环境问题：V15 种子脚本列数错误（system 模块已自行修复）；erp-server 聚合启动时 purchase/inventory 曾存在 `InvStockMapper` 类名冲突（已被对应模块修复，8080 共享实例现已正常运行）。

## 7. 验证记录（2026-09-05，全部通过）

- `mvn -pl erp-mrp -am compile -q` 通过；`npx tsc --noEmit` 通过。
- Flyway：隔离环境（erp_db_mrp）与共享 erp_db 均成功执行至 **v601**。
- 闭环（隔离环境 erp_db_mrp，端口 18082）：成品A + 子件B(用量2, 库存5) + 子件C(用量1, 库存100)
  1. 建 BOM → 审核 → 树形展开正确；
  2. MPS 10 件 → 运算批次 MR202609050001：PRODUCE A=10（建议日期=开工日−7），**PURCHASE B=15（毛20−库存5）**，C 库存充足不出现在建议；
  3. 转生产 → MO202609050001 下达 → 领料单自动带出应领（B20/C10）→ 全量审核被拒（B 可用5 需20）→ 部分实领(B5/C10)审核成功，inv_stock B 5→0、C 100→90，流水 PROD_USE −5/−10；
  4. 完工 10 良品 → **A 库存 0→10（+10）**，流水 PROD_IN +10，订单 FINISHED；
  5. 委外：发料扣 B10/C5（OUT_USE），入库 A+5（OUT_IN），应付 5×8.50=42.50（UNSETTLED）；
  6. 边界：BOM 循环引用正确抛异常；采购在途(8)参与扣抵（B 毛10 − 库存10 − 在途8 = 净0 无建议）。

## 8. 二期扩展：工艺路线 / 派工报工 / 能力计划（V611，2026-09-05）

### 8.1 新增数据表（6 张，V611__mrp_routing_dispatch.sql）

| 表 | 说明 | 关键约束 |
|---|---|---|
| mrp_work_center | 工作中心（CRP 产能计量单元） | uk(code WC+流水)；capacity_hours_per_day 为可用工时基数 |
| mrp_operation | 工序定义（标准工序库） | uk(code OP+流水)；default_work_center_id / setup_minutes / unit_minutes |
| mrp_routing | 工艺路线头（成品+版本） | uk(material_id, version)；状态机 DRAFT→ACTIVE→DISABLED（与 BOM 一致） |
| mrp_routing_step | 工艺路线工序步骤 | seq_no 路线内从1连续（服务层校验）；work_center_id / unit_minutes 可覆盖工序默认 |
| mrp_wo_step | 工单工序任务（派工/报工对象） | 生产订单下达时顺排生成；状态 PENDING_DISPATCH→DISPATCHED→PROCESSING→FINISHED |
| mrp_work_report | 工序报工明细（一次报工一行） | 回写 wo_step 累计合格/不合格/实际工时与状态 |

### 8.2 新增接口（均挂在 /api 下，JWT）

| 接口 | 方法 | 说明 |
|---|---|---|
| /mrp/work-centers（+/all /{id}） | GET/POST/PUT/DELETE | 工作中心 CRUD + 选择器 |
| /mrp/operations（+/all /{id}） | GET/POST/PUT/DELETE | 工序定义 CRUD + 选择器 |
| /mrp/routings（/{id} /{id}/tree /{id}/copy /{id}/audit /{id}/disable） | GET/POST/PUT/DELETE | 工艺路线 CRUD、树形查看、复制新版本、审核、停用 |
| /mrp/dispatch/list | GET | 任务看板（status + workCenterId 过滤，按工作中心分组） |
| /mrp/dispatch/wo-steps?workOrderId | GET | 订单工序进度 |
| /mrp/dispatch/{woStepId} | POST | 派工（PENDING_DISPATCH→DISPATCHED，assignee + 可选 planStartTime） |
| /mrp/report | POST | 工序报工（woStepId/employee/goodQty/badQty/workMinutes/qcNote） |
| /mrp/report/list | GET | 报工明细（woStepId / workOrderId 过滤） |
| /mrp/capacity/load?from&to&workCenterId | GET | 能力负荷：负荷工时/可用工时/负荷率%/超载清单 |

### 8.3 一期文件改动清单（联动改造，仅此一处）

- `MrpWorkOrderServiceImpl.release()`：下达成功后调用 `woStepService.generateForWorkOrder(wo)`——成品存在已审核(ACTIVE)路线时按工序序列顺排生成 mrp_wo_step（首道计划开始=计划开工日 08:00，空取当天；后续=前置结束；时长=单件工时×订单数量，分钟向上取整）；无路线静默跳过，一期流程不受影响；重复下达幂等跳过（countByWorkOrderId 防重）。

### 8.4 业务规则要点

1. **报工回写**：累计合格达到 plan_qty → FINISHED；首次报工 → PROCESSING；累计合格超计划数量拒绝（错误码 1000）。全工序完工不阻塞一期完工入库（进度以本表视图体现）。
2. **能力负荷口径（简版）**：负荷 = SUM(未完工 wo_step 的 plan_qty × unit_minutes) ÷ 60（按工作中心分组）；可用 = 每日产能 × 期间工作日数（周六日不计，默认期间 7 天）；负荷率 >100% 进超载列表。
3. **QC 挂点**：mrp_work_report.qc_note / mrp_wo_step.qc_pending_note（追加式，500 字符截断）为质量模块并行开发预留。
4. **删除防引用**：工作中心被工序默认/路线步骤/工单任务引用时拒绝删除；工序被路线步骤引用时拒绝删除；仅 DRAFT/DISABLED 路线可删（ACTIVE 须先停用）。

### 8.5 前端（erp-web）

- `src/api/mrp.ts`：追加 workCenterApi / operationApi / routingApi / dispatchApi / workReportApi / capacityApi 与对应类型。
- `src/pages/mrp/routings/RoutingPage.tsx`（新）：三 Tab——工艺路线（PageTable 列表 + 步骤子表编辑 Modal + 工序序列查看 + 复制新版本 + 审核/停用）、工作中心（每日产能工时维护）、工序定义（默认工作中心/单件工时）。
- `src/pages/mrp/dispatch/DispatchPage.tsx`（新）：两 Tab——任务看板（按工作中心分组卡片墙、派工 Modal、报工 Modal 含本任务报工历史、Progress 进度条、状态/工作中心过滤）、能力负荷（期间 RangePicker + 负荷率进度条 + 超载 Alert 清单）。
- `src/router/modules/mrp.tsx`：追加 `mrp/routings`、`mrp/dispatch` 两条路由（菜单项已在 MainLayout 就位）。
- 公共组件：PageHeader / PageTable / StatusTag / useDebounce / useSubmitting 全部按统一规范使用。

### 8.6 二期验证记录（2026-09-05，共享 erp_db + 8080 实例，全部通过）

1. `mvn -pl erp-mrp -am compile -q` 通过；`cd erp-web && npx tsc --noEmit` 0 错误。
2. Flyway V611 已执行（flyway_schema_history 版本 611，6 张新表就位）。
3. curl 闭环：2 工作中心(8h/天) → 3 工序(切割2min/焊接3min/包装1.5min) → BOM 审核 → 三工序路线 V1.0 审核 → 生产订单(物料3, qty=10, 计划开工09-07) 下达：
   - 生成 3 个 wo_step 顺排 08:00~08:20（切割）、08:20~08:50（焊接）、08:50~09:05（包装，覆盖 WC+1.5min）；
   - 派工 step1 → 报工合格 10 → FINISHED（进度 100%，实际工时 25min）；
   - step2 报工 4 合格 1 不合格 → PROCESSING + qc_pending_note 留痕；超报（4+7=11>10）被拒；
   - 能力负荷（09-07~09-13，工作日 5 天）：冲压 0.25h/40h=0.6%（完工 step1 不计入），装配 0.5h/40h=1.3%，Σ=数量×单件工时 正确；
   - 复制新版本 V2.0：DRAFT + 3 步骤 + 覆盖值保留；
   - 无路线物料订单下达：0 个 wo_step，一期流程不受影响；
   - 一期回归：领料审核（B 料 2000→1980）与完工入库（A 料 100→110，订单 FINISHED）均正常。

## 9. 集成已接线（V2101，2026-09-05 主线程集成）

并行期的裸 SQL 挂点已全部切换为同 JVM Service 直调（依赖方向单向
mrp → {inventory, cost, qc, workflow}），`MrpIntegrationMapper` 的
deductStock/increaseStock/insertMovement 及 selectStockQty 已删除（仅保留只读汇总、
采购在途、委外应付 fin_payable 直写与表探测）：

| 挂点 | 接线 | 说明 |
|---|---|---|
| 领料审核·库存 | `StockService.stockOut(FIFO)` | 批次子件按 first_in_at 拆批扣减（修复一期裸 SQL 对批次行多行命中的缺陷）；非批次汇总行；不足整单回滚 |
| 领料审核·成本 | `CostService.onStockOut("PROD_USE")` | 领料成本实时归集（月结转生产成本） |
| 完工入库·库存 | `StockService.stockIn`（FinishForm 可带 batchNo） | 成品批次入库（批次物料必填批次号由 StockService 强校验）；goodQty=0 只登记不良不入库 |
| 完工入库·成本 | `CostService.onStockIn("PROD_IN", amount=null)` | 按当前移动加权价估算（工单实际成本回填属后续迭代） |
| 完工入库·质检 | `QcInspectService.pendingOfSource("mrp_work_order", id)` 前置卡点 | 存在草稿 FQC 检验单→拒绝（提示单号）；无检验单→免检直过 |
| 委外发料/收货·库存 | `stockOut(FIFO)` / `stockIn`（OutsourceReceiveForm 可带 batchNo） | **委外 biz_type 并入 PROD_USE/PROD_IN**（InvBizType 枚举无 OUT_USE/OUT_IN；通过流水 biz_no=OS 单号 + remark"委外发料/委外完工入库"完整追溯，历史 OUT_USE/OUT_IN 流水不受影响） |
| 委外发料/收货·成本 | `onStockOut("PROD_USE")` / `onStockIn("PROD_IN")` | 加工费走应付不计入存货成本 |
| 订单下达·审批流 | `WorkflowService.startFlow("mrp_work_order")` 三段式 | SKIPPED/AGREED 直接下达；RUNNING 返回"已提交审批"（`wf_started`=1，V2101 加列；vars.amount=订单数量）；审批通过后再下达→执行下达；驳回后可重提 |
| MRP 运算 | inv_stock 只读 SUM（全仓合计，含批次行） | 口径不变 |

迁移脚本：`script/sql/migration/V2101__sales_actual_cost.sql`（mrp_work_order.wf_started）。
验证：V2101 集成回归（下单→下达→领料→FQC 卡点→完工→委外全链路 82 项断言全部通过）。

## 10. 转采购已接线（mrp → purchase，V612，2026-09-06）

- **接口**：`POST /api/mrp/suggestions/{id}/to-purchase`（body 可空或
  `{supplierId?, warehouseId?}`）；批量 `POST /api/mrp/suggestions/to-purchase-batch`
  `{ids:[], supplierId?, warehouseId?}`（ids 去重保序，同供应商合并一张草稿单，
  每条建议一行明细）。权限 `purchase:order:add`，@Idempotent + @OperLog。
- **校验**：建议必须存在、plan_type=PURCHASE、status=PENDING——任一不满足拒绝
  （重复转单/PRODUCE 类型/不存在分别提示）；批量任一建议不合法整批回滚。
- **草稿组装**（`MrpSuggestionServiceImpl.toPurchase/toPurchaseBatch` →
  `PurOrderService.create`）：订单日期=当天；明细 物料/数量=建议，price=0、taxRate=0
  （MRP 不感知价格，采购员补填），deliveryDate=suggest_date；订单备注
  「MRP 生成，批次号{batchNo}，建议ID {ids}」，行备注「MRP批次{batchNo}·建议#{id}」。
- **回写**：建议 status=CONVERTED、refType=PUR_ORDER、refId=采购订单 ID（同一事务）。
- **采购侧配套**（V612 + 代码）：pur_order.supplier_id 放开可空（仅草稿允许，
  `PurOrderServiceImpl.audit` 前强校验非空；`PurOrderForm.supplierId` 改可选，
  validateBaseInfo 空值跳过）。前端 MrpPage「转采购」按钮启用。
- **实测（共享 erp_db + 8080，全部通过）**：单条转单（物料3/数量50/建议日期 09-15）
  → PO202609060001 草稿（明细/交期/备注正确）→ 建议 CONVERTED（ref PUR_ORDER）；
  重复转被拒、PRODUCE 转被拒、不存在转被拒；批量 3 条同供应商 → 1 张单 3 行
  （PO202609060002），3 条建议全部 CONVERTED 指向同一订单；无供应商转单 → 草稿
  supplier 为空 → 审核被拒（「请先补填供应商再审核」）→ 补填供应商+单价后审核通过。

## 11. 三期扩展：倒冲领料 + BOM 替代料（V621，2026-09-06）

### 11.1 倒冲领料（backflush）

- **开关**：工单级 `mrp_work_order.pick_type`（V621 加列）——`DIRECT` 直接领料（默认，
  原有手工领料单流程不变）/ `BACKFLUSH` 倒冲；`backflush_warehouse_id` 可指定倒冲扣料仓
  （空 = 沿用 warehouse_id）。表单 `WorkOrderForm.pickType/backflushWarehouseId`，
  完工表单 `FinishForm.backflushWarehouseId` 可临时覆盖。
- **触发**：`MrpWorkOrderServiceImpl.finish`（完工入库）同事务调用
  `MrpBackflushService.backflush(wo, 本次良品, 扣料仓)`——良品入库 + 倒冲扣料要么全成
  要么全回滚。扣料仓兜底链：完工表单覆盖 → 工单倒冲仓 → 入库仓库。
- **用量**：每 BOM 子件应耗 = qty × 本次完工良品数 × (1 + loss_rate/100)（与手工领料
  应领公式同口径，但基数是"本次完工"而非订单总量，支持分次完工分次倒冲）。
- **出库**：经 `StockService.stockOut(FIFO, PROD_USE, bizId=倒冲领料单)` 唯一入口，
  批次物料自动跨批拆行；同事务 `CostService.onStockOut` 归集成本（与领料审核一致）。
- **缺料策略**：主料 + 全部替代料耗尽仍不足 → 抛 `erp.biz.mrp-sub-stock-not-enough`
  整体回滚。与 StockService 现有"库存不足报错、不允许负库存"策略保持一致。
- **单据留痕**：自动落 `mrp_material_issue`（issue_type=BACKFLUSH，直接 AUDITED）+
  明细行；倒冲工单禁止手工领料（`erp.biz.mrp-backflush-no-manual-issue`）。
- **inventory 侧新增（仅加方法）**：`StockService.getAvailableQty(warehouseId, materialId)`
  ——跨批次/库位行求和的可用量只读快照，供倒冲做主料/替代料分配规划；真正扣减仍走
  stockOut 行锁条件扣减，快照-扣减间并发差额由 stockOut 兜底回滚。

### 11.2 BOM 替代组

- **数据结构**：`mrp_bom_substitute`（V621 新表）——挂在 BOM 子件行
  （bom_item_id）上：主料 = 子件行自身物料，行 = 替代料（sub_material_id）+
  priority（1 最高）+ ratio（1 单位主料 = ratio 单位替代料）。唯一键
  (bom_item_id, sub_material_id)。
- **倒冲替代选择算法**（`MrpBackflushServiceImpl`）：需求先扣主料
  min(需求, 主料可用)；缺口按 priority 升序逐替代料补足——替代可折主料量 =
  替代可用 / ratio，实际扣替代料量 = 折主料量 × ratio；全部替代耗尽仍有缺口 → 报错回滚。
- **CRUD**：`/api/mrp/bom-substitutes?bomId=`（BOM 全组）、`?bomItemId=`（单子件行）、
  `POST /api/mrp/bom-substitutes/items/{bomItemId}`（整组重建）、
  `DELETE /api/mrp/bom-substitutes/{id}`。权限：查询 `mrp:bom:list`，维护
  `mrp:bom:substitute`（sys_menu 按钮 2615，V621 种子）。仅 DRAFT BOM 可维护；
  BOM 修改/删除时子件行级联清理替代组，复制新版本时随行复制。
- **前端**：BOM 管理页「替代料」按钮 → SubstituteModal（子件行展开维护替代组，
  整组保存）；生产订单页表单/列表支持领料方式（直接领料/倒冲）与倒冲仓库。

### 11.3 MRP 运算侧替代料：未接入（有意保留）

- **现状**：`MrpServiceImpl` 的 BOM 展开按主料计算毛需求，`mrp_bom_substitute` 仅
  倒冲链路消费。
- **原因**：MRP 净需求 = 毛需求 − 库存 − 在途，替代料参与运算需把"主料+替代组"
  合并为一个可用量池（含替代换算、跨仓口径、在途分配顺序），并影响建议数量与
  建议物料归属（主料缺多少、替代补多少、采购建议落在哪个料上），改动面涉及
  建议生成/转单/报表口径，需要独立设计评审后实施。
- **数据结构已就绪**：替代组表 + BOM 维护界面均已可用，后续接入 MRP 运算时
  无需再动表结构（必要时补充"替代参与运算"开关列即可）。
