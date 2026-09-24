# 成本管理模块（erp-cost）

> 对标金蝶 K3「存货核算 + 生产成本」：移动加权平均计价、月末结账（自动转账凭证）、
> 工单料工费归集、成本分析。后端：`erp-backend/erp-cost/`；前端：`erp-web/src/pages/cost/`、
> `src/api/cost.ts`、`src/router/modules/cost.tsx`；SQL：`script/sql/migration/V801__cost_init.sql`（cost 模块使用 V80x 段）。

## 1. 表结构（V801，3 张自有表）

| 表 | 说明 | 关键约束 |
|---|---|---|
| `cost_flow` | 存货计价流水（移动加权运行台账） | qty 正入负出、amount 同号；unit_cost 恒非负（入库行=本次入库单价，出库行=当时加权价）；balance_qty/balance_cost=本行过账后运行余额；period yyyyMM 取业务日期；movement_id 关联 inv_movement（sync 回填） |
| `cost_period` | 存货核算期间 | uk(period)；status OPEN→CLOSED 单向；结账快照 out_amount/voucher_id/voucher_no/settle_by/settle_time |
| `cost_work_order` | 工单成本归集（费/工时手工分摊+订单快照） | uk(work_order_id)；labor_hours/overhead_amount 整体覆盖式录入；料/工不入库、查询时实时归集 |

计价维度：**物料 × 全仓**加权（warehouse_id 仅溯源，与 inv_stock 的仓库粒度互补）。

跨模块约定表（只按约定列读写，不 import 对方代码，不建物理外键）：

| 表 | 归属 | 用途 |
|---|---|---|
| `inv_movement` | inventory V401 | sync 重放数据源（id/biz_type/biz_id/biz_no/warehouse_id/material_id/qty/create_time） |
| `pur_instock_detail` | purchase V201 | PUR_IN 计价金额（instock_id+material_id → SUM(amount)） |
| `mrp_work_order` / `mrp_material_issue(_item)` | mrp V601 | 工单头与已审核领料（issue.status='AUDITED'，实领 actual_qty） |
| `hr_piece_wage_detail` | hr 二期（**并行开发中**） | 计件工资：约定列 `work_order_id, employee_id, amount`（+规范审计字段）。表未建成前自动降级为 0（存在性 60s 缓存探测，建表后自动生效无需重启） |
| `fin_voucher` / `fin_voucher_entry` | finance V501 | 月结自动凭证 INSERT（DRAFT，待会计复核过账） |
| `fin_subject` | finance V501 | 按编码解析科目：6401 主营业务成本 / 5001 生产成本 / 1405 库存商品 |
| `base_material` / `base_warehouse` | basedata V101 | 名称回显 |

## 2. CostService —— 对外计价 SPI（跨模块核心服务，全系统唯一计价入口）

```java
package com.erp.cost.service;

public interface CostService {

    /**
     * 入库计价过账（移动加权）：新单价 = (原数量×原单价 + amount) / (原数量 + qty)。
     * amount=null 时按当前移动加权价估算（无成本历史按 0）；期间（bizDate 所在月）已结账则拒绝。
     */
    void onStockIn(Long materialId, String bizType, String bizNo, BigDecimal qty, BigDecimal amount);

    /** 全参重载：warehouseId 仅溯源；bizId 关联来源单据；bizDate 决定期间归属 */
    void onStockIn(Long materialId, Long warehouseId, String bizType, String bizNo,
                   Long bizId, BigDecimal qty, BigDecimal amount, LocalDate bizDate);

    /** 按当前移动加权单价计算出库成本（纯计算不过账，HALF_UP 2 位；结存 0 退化最后单价；无历史返回 0） */
    BigDecimal calcOutCost(Long materialId, BigDecimal qty);

    /** 出库计价过账：成本=当前加权单价×qty，落 qty<0 流水，运行余额扣减（扣至 0 清零消尾差）；返回出库成本 */
    BigDecimal onStockOut(Long materialId, String bizType, String bizNo, BigDecimal qty);

    /** 全参重载（同 onStockIn） */
    BigDecimal onStockOut(Long materialId, Long warehouseId, String bizType, String bizNo,
                          Long bizId, BigDecimal qty, LocalDate bizDate);

    /** 物料当前移动加权成本余额（qty/amount/unitCost，只读） */
    MaterialBalanceVO currentBalance(Long materialId);
}
```

bizType 取值与 inventory `InvBizType` 编码一致（字符串传入，勿依赖对方枚举类）。

### 一期模块接线清单（主线程后续接入）

| 模块 | 时机 | 调用 |
|---|---|---|
| purchase | 采购入库单审核（现有直接 INSERT inv_movement 处） | `costService.onStockIn(materialId, whId, "PUR_IN", 入库单号, 入库明细ID, qty, 入库金额, 入库日期)`（入库金额=明细 amount，与应付口径一致） |
| purchase | 采购退货审核 | `costService.onStockOut(materialId, whId, "PUR_RET", 退货单号, 退货单ID, qty, 退货日期)` |
| sales | 销售出库单审核 | `costService.onStockOut(materialId, whId, "SAL_OUT", 出库单号, 出库单ID, qty, 出库日期)` |
| sales | 销售退货审核 | `costService.onStockIn(materialId, whId, "SAL_RET", 退货单号, 退货单ID, qty, null, 退货日期)`（null=按当前加权价回冲） |
| mrp | 领料单审核 | `costService.onStockOut(materialId, whId, "PROD_USE", 领料单号, 领料单ID, qty, 领料日期)` |
| mrp | 完工入库 | `costService.onStockIn(materialId, whId, "PROD_IN", 工单号, 工单ID, qty, null, 入库日期)`（工单实际成本接通后可传实际金额） |
| inventory | 其他出入库/盘点/调拨审核 | OTH_IN 带金额调 onStockIn；OTH_OUT/盘亏调 onStockOut；盘盈 onStockIn(…, null, …)；MOVE 双向各一行（全仓加权下金额自然抵消） |

接线前存量数据用 `POST /cost/sync` 重算；接线后每笔业务实时过账，两者走同一
`CostLedger` 记账引擎，重算与实时结果一致。

### 计价规则与一致性约定

1. 舍入全链统一：金额 2 位、单价 6 位，HALF_UP；
2. 出库成本 = 出库时点移动加权单价 × 数量；**余额扣至 0 时金额清零**，消除连续舍入尾差；
3. 并发：每次过账 `SELECT … FOR UPDATE` 锁该物料最新一行流水，同物料计价串行；
4. 已结账（CLOSED）期间拒绝过账（防止结账数据被追溯污染）；
5. 无成本历史的物料出库按 0 单价（新物料先 PUR_IN 计价后再出库才有成本）。

## 3. 功能与接口（统一前缀 /api，JWT 登录）

### 3.1 存货计价（`/cost`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/cost/flows?pageNum&pageSize&period&materialId&bizType&keyword` | 计价流水分页（含物料/仓库回显、运行余额） |
| POST | `/cost/sync?from&to` | 从 inv_movement 重算 [from,to] 期间计价（from 可空=最早流水起）。**保护规则**：窗口含 CLOSED 期间、或其后仍有 CLOSED 期间 → 拒绝；实际重算起点自动推到最后结账期间次月，余额从其台账续算。PUR_IN 取采购入库明细金额，其余按加权价推算 |
| POST | `/cost/hooks/receive` | 单笔业务补录：`{materialId, warehouseId?, bizType, bizNo, qty>0, amount?(入库可选), bizDate?}`；入库方向可带金额，出库方向按当前加权价 |

### 3.2 月末核算（`/cost`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/cost/periods` | 核算期间列表（倒序） |
| POST | `/cost/settle/{period}` | 月末结账：校验 OPEN（不存在则建）→ 汇总本期出库成本 → 自动生成转账凭证 → CLOSED。凭证分录：借 6401 主营业务成本（SAL_OUT+OTH_OUT+PUR_RET+盘亏）、借 5001 生产成本（PROD_USE，>0 才有该行）、贷 1405 库存商品（合计），凭证字「转」、日期=期间最后一日、状态 DRAFT 待复核；出库成本为 0 不生成凭证。后续期间已 CLOSED 时拒绝（按序结账） |
| GET | `/cost/settle/summary/{period}` | 收发存汇总：按物料 期初数量/金额、入/出、期末（=期初+入−出）+ 全物料合计 |

### 3.3 工单成本归集（`/cost/work-orders`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/cost/work-orders?pageNum&pageSize&keyword&status` | 工单成本分页：料/工/费/合计/单位成本 |
| GET | `/cost/work-orders/{id}` | 详情：领料明细（×移动加权价）、工资明细（hr 约定表）、费用分摊、三费合计、单位成本（总成本/完工数量，完工 0 按订单数量） |
| POST | `/cost/work-orders/{id}/overhead` | 制造费用分摊录入（整体覆盖）：`{overheadAmount≥0, laborHours≥0, remark?}` |

口径说明：料采用**现行移动加权价**（时点价需一期模块接线后才有历史流水）；
工 = `hr_piece_wage_detail` 按 work_order_id 汇总（表未建成=0）；费 = 手工分摊。

### 3.4 成本分析（`/cost/analysis`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/cost/analysis/materials?period` | 物料收发存汇总（同 3.2 summary） |
| GET | `/cost/analysis/trend?from&to` | 月度趋势：入库金额/出库成本/主营业务成本(SAL_OUT)/生产领料成本(PROD_USE)/期末存货余额（期初+逐月收支推算） |

## 4. 前端

- `pages/cost/pricing/CostFlowPage.tsx`：计价流水（期间/物料/单据类型筛选）+ 期间重算 Modal + 单笔补录 Modal；
- `pages/cost/settle/SettlePage.tsx`：期间列表 + 收发存汇总表（共享 `components/SummaryTable.tsx`）+ 结账操作（Popconfirm）；
- `pages/cost/analysis/AnalysisPage.tsx`：Recharts 组合图（主营业务成本/生产领料柱状 + 期末存货余额折线）+ 物料收发存汇总；
- `api/cost.ts`、`router/modules/cost.tsx`（导出 `costRoutes`：cost/pricing、cost/settle、cost/analysis，与 MainLayout 菜单一致）。

## 5. 关键实现位置

| 内容 | 文件 |
|---|---|
| 移动加权记账引擎（实时/重算共用） | `service/impl/CostLedger.java` |
| 计价 SPI 实现 | `service/impl/CostServiceImpl.java` |
| sync 重算 / hook 补录 | `service/impl/CostFlowServiceImpl.java` |
| 月结 + 自动凭证 | `service/impl/CostSettleServiceImpl.java` |
| 工单料工费归集 + hr 降级探测 | `service/impl/CostWorkOrderServiceImpl.java` |
| 趋势/收发存 SQL | `resources/mapper/cost/CostFlowMapper.xml` |
| 跨模块约定表 SQL | `resources/mapper/cost/CostIntegrationMapper.xml` |
| 凭证号生成（与 finance 共用 PZ 流水空间） | `support/CostBizNoGenerator.java` |

## 6. 验证记录（2026-09-05）

- `mvn -pl erp-cost -am compile -q` 通过；`npx tsc --noEmit` cost 相关 0 错误；
- Flyway V801 执行成功（erp_db：cost_flow / cost_period / cost_work_order 建表，flyway_schema_history 801 success=1）；
- 数值自测（curl，验证实例含 erp-cost 全模块）：
  1. `POST /cost/sync?to=202609` 重放 46 条 inv_movement → 10 物料计价台账；每物料期末 balance_qty 与
     inv_movement 合计**全部一致**（SQL 逐物料比对无差异）；
  2. 补录入库 100@10 + 100@12 → 运行余额 200/2200，**移动加权价 11**；
     出库 50 → unit_cost=11.000000、amount=-550.00、结存 150/1650（**出库成本 550**）；
  3. 重新 sync 后重放同一组业务 → 结果逐字段一致（重算与实时同一引擎，确定性成立）；
  4. `POST /cost/settle/202609`：出库成本 1003（销售 550/生产领料 3/其他 450）→ 自动凭证
     PZ202609050001（借 6401=1000、借 5001=3、贷 1405=1003，借贷平衡，DRAFT），期间 CLOSED；
  5. 保护校验全部生效：重复结账拒绝 / 结账后 sync 202609 拒绝 / 结账后补录 202609 业务拒绝 /
     非法期间与非法业务类型拒绝；
  6. 收发存汇总（202609：入 8455/出 1003/期末 7452，期初+入−出自洽）、月度趋势
     （salOutCost=550、stockBalance 与汇总期末一致）、工单成本（费分摊 300.50 → 单位成本 60.10）、
     hr 表未建成时工资降级为 0 —— 均验证通过。

## 7. 遗留问题与协作事项

1. **一期接线未完成**：采购/销售/生产审核事务尚未调用 CostService（见 2 节接线清单，主线程任务）；
   接线前依赖 `POST /cost/sync` 重算或 hooks 补录。
2. **反结账未实现**：CLOSED 为单向终态；如需重开历史期间需 DBA 介入或后续迭代「反结账」功能。
   注意：当前共享库 erp_db 已将 202609 结账（验证要求），后续期间业务请在 202610 及以后过账，
   或由主线程决策重置 cost_period。
3. **工费口径**：料用现行移动加权价（非领料时点价）；hr_piece_wage_detail 建表前工费显示 0（自动探测，
   60s 缓存）；HR 建表时请按规范带上 deleted 等审计字段，且**勿逻辑删除工资行**（本模块按 deleted=0 过滤）。
4. 结账凭证为 DRAFT 草稿，需会计在财务模块复核过账后进入试算平衡/科目汇总口径。
5. PROD_IN（生产入库）暂按当前加权价估算入账，工单实际成本（料工费归集结果）回填属后续迭代。
6. **集成阻塞（非本模块）**：全量聚合 jar（erp-server）当前无法启动 —— equipment 模块的
   `com.erp.equipment.support.BizNoGenerator` 与 inventory 的同名类 Bean 名冲突
   （`bizNoGenerator`，ConflictingBeanDefinitionException）。equipment 侧仿照
   FinanceBizNoGenerator/CostBizNoGenerator 改名即可。期间 8080 由并行任务以部分模块 classpath 实例占用，
   cost 模块验证在 18080 端口实例完成（含 erp-cost）。
