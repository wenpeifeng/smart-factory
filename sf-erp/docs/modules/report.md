# 报表中心模块（erp-report）设计文档

> 版本：v1.1（2026-09-06）· 范围：经营驾驶舱 + 报表中心分组目录（8 组 14 张：4 张既有多维报表 + **10 张制造业标准报表**）+ 通用 CSV 导出 + 单据打印（见 print.md）
> 后端：`erp-backend/erp-biz/erp-biz-report/`；前端：`erp-web/src/pages/report/`、`src/api/report.ts`、`src/router/modules/report.tsx`
> SQL：**零新表纯聚合**（无 Flyway 脚本，全部按约定表结构只读 JOIN）

## 1. 模块定位

报表中心是跨模块的**只读聚合层**：不拥有任何业务表、不 import 任何业务模块 Service，
全部指标通过自定义 SQL（`resources/mapper/report/ReportMapper.xml`）按各模块建表脚本
（V1xx/V2xx/V3xx/V4xx/V5xx/V6xx）的约定列直接 JOIN 查询 —— 与 sales/purchase 等
模块的跨模块协作约定完全一致。pom 虽依赖全部业务模块，仅为随 erp-server 聚合启动，
代码层面零业务依赖。

驾驶舱同步改造：首页 `pages/dashboard/Dashboard.tsx` 改为**复用** `ReportDashboard`
组件（`export { default } from '@/pages/report/dashboard/ReportDashboard'`）。
选择复用而非重定向到 /report/dashboard 的理由：/dashboard 是登录默认落地页，
复用组件零跳转、无双次导航，菜单「工作台」高亮保持正确；空态/加载/错误状态由
组件内部统一处理，重定向无法携带这些上下文。

## 2. 指标口径（每项含 SQL 语义）

统一约定：只统计「已审核」单据；日期一律**半开区间 [from, to)**；SUM 全部
`COALESCE(...,0)`；金额/数量字段 VO 全部 `BigDecimal`（DECIMAL 精度直传）。

### 2.1 驾驶舱 GET /report/dashboard（本期=当月，环比=上一自然月整月）

| 指标 | 口径（等价 SQL） | 说明 |
|---|---|---|
| 本月销售额 salesAmount | `SELECT COALESCE(SUM(total_amount),0) FROM sal_outstock WHERE deleted=0 AND status=10 AND out_date>=[月初) AND out_date<[次月初)` | sal 表无税额列，amount 即**不含税**口径；退货不冲减（与 sales 模块统计口径一致） |
| 本月采购额 purchaseAmount | 同上，`pur_instock.total_amount`（按 instock_date） | **含税价税合计**（purchase 模块金额口径） |
| 库存总值 inventoryValue | `SUM(s.qty × 价格)`，价格 = `采购加权价 COALESCE(ref_cost, 0)`；加权价=`SUM(明细qty×price)/SUM(qty)`（全部已审核采购入库） | 时点值无环比；「最近加权价」取全量已审核采购的移动加权近似，无采购记录回退 base_material.ref_cost（与销售统计成本口径一致），仍无则 0 |
| 应收余额 receivableBalance | `SELECT COALESCE(SUM(amount−settled_amount),0) FROM fin_receivable WHERE deleted=0` | 当前时点快照；红字应收（负金额）自然冲减；核销不回溯历史期间 |
| 应付余额 payableBalance | 同上 `fin_payable` | 同上 |
| 待办审批数 pendingApprovalCount | `SELECT COUNT(*) FROM wf_task WHERE deleted=0 AND status='PENDING'` | **优雅降级**：先查 information_schema 确认表存在，不存在/异常 → 0（workflow 模块未上线时不阻断驾驶舱） |
| 库存预警数 stockWarningCount | `base_material LEFT JOIN inv_stock（行=仓库×物料）WHERE COALESCE(s.qty,0) < safety_stock` | 与 inventory 模块 `/inventory/warnings` **完全同口径**（无库存行物料按 0 计） |
| 本月生产完工数 productionFinishQty | `SELECT COALESCE(SUM(finish_qty),0) FROM mrp_work_order WHERE deleted=0 AND finish_qty>0 AND update_time∈[月初,次月初)` | mrp_work_order 无完工日期列，以「本期有过完工动作（update_time 落期）」的订单 finish_qty 现值合计近似；**部分完工（PRODUCING）的良品数计入本期** |
| 走势 trends | 销/采按 `DATE_FORMAT(业务日期,'%Y-%m')` 分月汇总，取近 6 个月 | 无单据月份 Java 补零，横轴连续 |
| 仓库价值分布 warehouseValues | 库存总值明细按 warehouse_id 二次聚合 | 明细同「库存总值」 |
| 当月销售 TOP5 topMaterials | 已审核出库明细按 material_id 汇总 `SUM(d.amount)` 降序 LIMIT 5 | 当月=[月初,次月初) |

### 2.2 销售明细 GET /report/sales/detail?from&to&customerId&groupBy

- 数据源：`sal_outstock(已审核) JOIN sal_outstock_detail LEFT JOIN base_material / base_customer`；
- 列：数量 `SUM(d.qty)`、不含税额 `SUM(d.amount)`、参考成本 `SUM(d.qty×COALESCE(ref_cost,0))`、毛利 `SUM(amount−qty×ref_cost)`；
- groupBy：`material`（默认）/ `customer` / `month`（yyyy-MM）；from/to 可空（全量），customerId 可空；
- 排序：维度维度=material/customer 按金额降序，month 按月升序；非法 groupBy → BusinessException(1000)。

### 2.3 采购明细 GET /report/purchase/detail?from&to&supplierId&groupBy

- 数据源：`pur_instock(已审核) JOIN pur_instock_detail LEFT JOIN base_material / base_supplier`；
- 列：数量、**价税合计（含税）** `SUM(d.amount)`；无成本/毛利列（采购无参考售价概念）；
- groupBy：`material` / `supplier` / `month`，行为同销售明细。

### 2.4 库存库龄 GET /report/inventory/aging

- 明细粒度：仓库×物料（`inv_stock WHERE qty>0`）；
- 库龄锚点：该仓物料**最后一次入库方向流水**时间 —— `(SELECT MAX(create_time) FROM inv_movement WHERE qty>0 GROUP BY material_id,warehouse_id)`（qty 正入负出约定）；
- 分桶（Java 层）：`ageDays=DATEDIFF(CURDATE(),DATE(last_in_time))` → 0-30 / 31-90 / 91-180 / 180+ 天；无入库流水 → 「无入库流水」桶（仅在存在时展示）；
- 输出：`totalQty` + 每桶 `qty`、`ratio`（4 位小数 HALF_UP）+ 全量明细（含加权价/库存价值，价格口径同 2.1）。

### 2.5 财务汇总 GET /report/finance/summary?period=yyyy-MM（默认当月）

| 指标 | 口径 |
|---|---|
| 营业收入 revenue | 期间存在 **POSTED** 凭证 → `fin_voucher_entry JOIN fin_voucher(POSTED) JOIN fin_subject WHERE s.code LIKE '6001%'` 的**贷方**合计（source=VOUCHER）；否则业务降级：当期已审核销售出库不含税额（source=BIZ） |
| 营业成本 cost | 同上：`6401%` 科目**借方**合计；降级：`SUM(出库qty×ref_cost)` |
| 毛利/毛利率 | revenue−cost；毛利率=毛利/收入×100（2 位 HALF_UP，收入 0 → 0） |
| 应收/应付余额 | 同 2.1 时点快照 |
| 现金流 | `fin_account_flow` 按 biz_date 落期：INCOME 合计 cashIn / EXPENSE 合计 cashOut / 净额 cashNet |

> 凭证口径判定粒度：只要期间有任意 POSTED 凭证即整体走凭证口径（无凭证才整体降级），
> 避免"半凭证半业务"的混合口径。

### 2.6 CSV 导出（&export=csv）

- 同一接口加 `export=csv` 参数即返回 `text/csv;charset=UTF-8` 附件
  （`Content-Disposition: attachment; filename="..."`，文件名 ASCII：`sales_detail_material_20260905.csv`）；
- 首行表头（中文，随 groupBy 变化）；UTF-8 **BOM** 保证 Excel 中文不乱码；RFC 4180 转义；CRLF 行尾；
- 数字用 `BigDecimal.toPlainString()`（无科学计数法），价格/价值/成本/毛利统一 ROUND 2 位、加权价 4 位、数量保持 4 位；
- 导出范围与当前筛选完全一致（前端把当前查询参数透传）。

## 3. 后端结构

```
com.erp.report
├── controller/   ReportDashboardController / ReportSalesController / ReportPurchaseController
│                 / ReportInventoryController / ReportFinanceController（JSON 与 CSV 同方法切换）
│                 + ReportMonthlyController / ReportProductionController / ReportHrController（v1.1 新增）
├── service/      ReportDashboardService + ReportQueryService（接口+impl）
│                 + ReportStandardService（v1.1：10 张制造业标准报表，接口+impl）
├── mapper/       ReportMapper（纯 XML 自定义 SQL，resources/mapper/report/ReportMapper.xml）
├── dto/query/    SalesDetailQuery / PurchaseDetailQuery / FinanceSummaryQuery
│                 + MonthlySummaryQuery / SalesMarginQuery / SupplierPerformanceQuery / InventoryMonthlyQuery
│                 / SluggishQuery / ExecutionQuery / WorkhoursQuery / MaterialLossQuery / ExpenseQuery
│                 / PiecewageQuery（v1.1）
├── dto/vo/       DashboardVO / ReportRowVO / InventoryAgingVO / FinanceSummaryVO
│                 + MonthlySummaryVO / SalesMarginVO / SupplierPerformanceVO / InventoryMonthlyVO / SluggishVO
│                 / WorkExecutionVO / WorkhoursVO / MaterialLossVO / ExpenseVO / PiecewageVO（v1.1）
└── support/      CsvExporter（BOM/RFC4180/附件头）+ AmountInWords（打印大写）
```

- CSV 分支直接写 Servlet 响应流后返回 null（Spring 视为已处理，不再包 Result），
  因此这几个接口返回类型为 Object（Swagger 注解仍完整）；
- 不分页：报表中心为全量聚合导出场景，当前数据量级（明细行数十级）无压力；
  行数增长后可在 XML 加 LIMIT 或改游流式导出（遗留项）。

## 4. 前端

| 文件 | 说明 |
|---|---|
| `src/api/report.ts` | 类型与请求 + `downloadReportCsv`（blob 下载，独立 axios 实例带 JWT + 401 处理，绕开 JSON 拦截器）；v1.1 增 10 张标准报表的类型与请求 |
| `src/pages/report/dashboard/ReportDashboard.tsx` | 经营驾驶舱：4 主指标卡（销/采带环比涨跌箭头）+4 次指标卡（应付/待办/预警/完工）+ 近 6 月渐变面积走势 + 仓库价值分布 Bar + 当月 TOP5 物料 Bar；全空 → AntD Empty「先录入业务单据」引导 |
| `src/pages/report/center/ReportCenter.tsx` | **分组目录**（v1.1）：左侧 Sider+Menu（`type='group'`：经营/销售/采购/库存/生产/财务/人事/多维）+ 右侧参数面板 + Table/图表；面板全部 lazy 分包，目录切换 `Suspense key` 重建；路由不变（report/center 单页内分组，默认选中经营月报） |
| `center/SalesPane.tsx` `PurchasePane.tsx` | 筛选栏（RangePicker/客商下拉/维度切换）+ Table（含合计行）+ 导出 CSV；to 参数自动 +1 天（后端半开区间）；v1.1 归入「多维」组 |
| `center/AgingPane.tsx` | 分桶统计卡（占比 Progress + 语义色）+ 明细表（桶筛选/天数排序）+ 导出；归入「库存」组 |
| `center/FinancePane.tsx` | 期间 MonthPicker + 指标卡阵（营收/成本带口径 Tag）+ 现金流净额 + 导出；归入「财务」组 |
| `center/MonthlySummaryPane.tsx` | v1.1 经营月报：月份选择 + 3×4+3 指标卡阵（完工/产销/采/毛利/费用/应收应付/库存总值）+ 口径脚注 + 导出 |
| `center/MarginPane.tsx` | v1.1 销售毛利：区间 + 维度（物料/客户）+ 表（毛利/毛利率列合计）+ 导出 |
| `center/SupplierPerformancePane.tsx` | v1.1 供应商绩效：区间 + 表（及时率/合格率 Tag 语义色，无数据 N/A）+ 导出 |
| `center/InventoryMonthlyPane.tsx` | v1.1 收发存月报：月份 + 11 列表（期初/入/出/期末 × 数量金额）+ 合计行 + 导出 |
| `center/SluggishPane.tsx` | v1.1 呆滞物料：天数 InputNumber + 呆滞合计统计 + 表（占比/库龄 Tag/从未出库标记）+ 导出 |
| `center/ExecutionPane.tsx` | v1.1 订单执行跟踪：区间 + 完工率 Progress + 状态/超期 Tag + 超期计数 + 导出 |
| `center/WorkhoursPane.tsx` | v1.1 报工工时：区间 + 工作中心下拉（`workCenterApi.listAll`）+ 维度（工序/员工）+ 合计行 + 导出 |
| `center/MaterialLossPane.tsx` | v1.1 材料损耗：区间 + 表（超耗红/节约绿）+ 合计行 + 导出 |
| `center/ExpensePane.tsx` | v1.1 费用分析：期间 + 部门小计 Descriptions + 部门筛选 Table + 导出 |
| `center/PiecewagePane.tsx` | v1.1 计件工资：期间 + 表（员工×工序）+ 合计行 + 导出 |
| `src/pages/report/shared.tsx` | fmtMoney/fmtQty/fmtRatio/环比 DeltaTag（涨绿跌红）/ChartEmpty/骨架/SourceTag |
| `src/router/modules/report.tsx` | `reportRoutes`：`report/dashboard`、`report/center`（主线程合并进 router/index.tsx，本轮已加 2 行合并） |
| `src/pages/dashboard/Dashboard.tsx` | 复用导出 ReportDashboard（见 §1 改造说明） |
| `scripts/ssr-report-center.mjs` | v1.1 分组目录 SSR 断言脚本（vite.ssrLoadModule + renderToString，24 项断言） |

动效与视觉对齐原演示版：Framer Motion 错峰入场（0.08s 级联、0.35s ease-out）、
`erp-hover-card` 上浮、Recharts 渐变面积图；服务端态全部 TanStack Query
（queryKey 带入参）。

## 5. 验证记录（2026-09-05，隔离环境 erp_db_report + 端口 18083 + Redis db2）

> 8080 共享实例当时被并行模块（equipment/inventory `BizNoGenerator` 同名 Bean、
> hr/mrp `SeqNoServiceImpl` 同名 Bean）的启动冲突占用，为不阻塞验证采用隔离库实例；
> 两处冲突已由对应模块改名修复（EqpBizNoGenerator / HrSeqNoServiceImpl），
> 修复后同一 jar 在 8080 可正常启动。

- `mvn -pl erp-report -am compile -q` 通过；`mvn -q -pl erp-server -am package -DskipTests` 通过；
- Flyway：隔离库 25 个脚本全部执行成功（本模块**零新表零脚本**）；
- `cd erp-web && npx tsc --noEmit` 0 错误；`npx vite build` 通过；
- 演示数据（全部经 API 产生）：采购订单→入库审核（铝板100×88+螺丝2000×1.8=12,400 元）、
  销售订单→出库审核（铝板30×120+螺丝500×3.5=5,350 元）、收款核销 3,000、
  过账凭证（6001 贷 5,000 / 6401 借 3,500）、生产订单完工 8（PRODUCING 部分完工）。

### 5.1 驾驶舱 curl vs 手工 SQL 核对（8/8 一致）

| 指标 | API 值 | 手工 SQL 值 | 核对 SQL |
|---|---|---|---|
| 本月销售额 | 5350.00 | 5350.00 | `SUM(total_amount) FROM sal_outstock WHERE status=10 AND out_date∈[2026-09-01,2026-10-01)` |
| 上月销售额 | 0.00 | 0.00 | 同上区间 [08-01,09-01) |
| 本月采购额 | 12400.00 | 12400.00 | `SUM(total_amount) FROM pur_instock WHERE status=10 AND instock_date∈[09-01,10-01)` |
| 应收余额 | 2350.00 | 2350.00 | `SUM(amount−settled_amount) FROM fin_receivable`（5350−3000 已核销） |
| 应付余额 | 12400.00 | 12400.00 | `SUM(amount−settled_amount) FROM fin_payable` |
| 库存预警数 | 0 | 0 | `COUNT(base_material LEFT JOIN inv_stock WHERE COALESCE(qty,0)<safety_stock)`（铝板 70≥50 不预警） |
| 本月完工数 | 8.0 | 8.0 | `SUM(finish_qty) FROM mrp_work_order WHERE finish_qty>0 AND update_time∈本月` |
| 库存总值 | 11260.00 | 11260.00 | 70×88(加权)+1500×1.8(加权)+8×300(ref_cost 回退)=6160+2700+2400 |
| 待办审批数 | 0 | —（wf_task 表不存在，优雅降级 0） | information_schema 探测 → 0 |

走势/分布/TOP 亦核对：trends 仅 2026-09 有值(5350/12400)、主仓库 11260、
TOP=[铝板3600, 螺丝1750]。

### 5.2 多维报表与 CSV

- 销售明细三种分组数值交叉一致：物料合计(3600+1750=5350)=客户维度(5350)=月维度(5350)，
  成本 2700+1000=3700、毛利 900+750=1650 三维度一致；customerId 过滤生效；
- 采购明细：物料(8800+3600=12400)=供应商(12400)=月(12400)；
- 库龄：totalQty=1578（70+1500+8），全部落入 0-30 天桶 ratio=1.0，锚点=最后一次
  PUR_IN/PROD_IN 流水时间；
- 财务汇总 2026-09：VOUCHER 口径（revenue 5000/cost 3500/毛利 1500/毛利率 30%/现金净额 3000）；
  2026-08：BIZ 降级口径全 0；`period=2026-13` → 参数错误；
- CSV：`Content-Type: text/csv;charset=UTF-8` + `Content-Disposition: attachment` ✓；
  首行中文表头 ✓（销售物料 6 列/月份 5 列/库龄 9 列/财务 2 列）；
  行数 ✓（销售=1+2、采购=1+1、库龄=1+3、财务=1+12）；BOM+CRLF ✓；
  空结果导出仅表头行 ✓；加权价 88.0000 / 价值 6160.00 等小数位规整 ✓。

## 6. v1.1 报表中心标准化：分组目录 + 10 张制造业标准报表（2026-09-06）

对标金蝶/用友/帆软制造业标准报表，把报表中心从 4 Tab 改造为**分组目录**并新增 10 张刚需报表。
全部沿用既有约定：只读 JOIN、只统计已审核单据、半开区间、`COALESCE` 兜底、`&export=csv` 导出。

### 6.1 分组目录结构（路由不变，report/center 单页内分组）

| 分组 | 报表 |
|---|---|
| 经营 | 经营月报 |
| 销售 | 销售毛利分析 |
| 采购 | 供应商绩效 |
| 库存 | 收发存月报 / 呆滞物料 / 库存库龄（既有） |
| 生产 | 订单执行跟踪 / 报工工时统计 / 材料损耗分析 |
| 财务 | 财务汇总（既有）/ 费用分析 |
| 人事 | 计件工资汇总 |
| 多维 | 销售明细 / 采购明细（既有多维明细） |

### 6.2 新增 10 张报表接口与口径

| # | 接口 | 口径要点 |
|---|---|---|
| 1 | `GET /report/monthly-summary?month=yyyy-MM` | 一页纸指标：完工工单数（区间内 update_time 落期且 finish_qty>0 的工单数）+产量（finish_qty 现值合计，驾驶舱同口径）+销量/销售额（已审核出库；销售额=出库头 total_amount 不含税口径）+采购额（已审核入库价税合计）+毛利/毛利率（**与财务汇总完全同口径**：期间有 POSTED 凭证取 6001 贷/6401 借，无凭证降级业务口径）+费用合计（POSTED 凭证 66 前缀损益科目借方：6601/6602/6603）+应收/应付余额（**当前时点快照**，无期间余额表）+库存总值（时点，加权价/ref_cost） |
| 2 | `GET /report/sales/margin?from&to&groupBy=customer\|material` | 销售额=明细 amount；**成本=COALESCE(d.actual_cost, d.qty×ref_cost)**（CostService 记账行取实际成本行合计值，未记账历史行回退参考成本）；毛利=额−成本；毛利率=毛利/额×100（2 位 HALF_UP，额 0→0）；返回 rows+total（total 毛利率按合计口径重算） |
| 3 | `GET /report/purchase/supplier-performance?from&to` | 按供应商聚合三段：①订单数/订单金额=pur_order（status∈10/20/30，剔除已取消）按 order_date 落期；②入库金额+到货及时率=已审核 pur_instock（instock_date 落期）JOIN 明细行 JOIN pur_order_detail，**及时=入库日期 ≤ COALESCE(订单行 delivery_date, 订单 order_date)**，按明细行占比；③质检合格率=qc_inspect（source_type='pur_instock'，inspect_date 落期）SUM(qualified_qty)/SUM(sample_qty)。**无到货行/无质检数据 → rate=null（前端 N/A）**；仅展示区间内有业务的供应商 |
| 4 | `GET /report/inventory/monthly?month=yyyy-MM` | **物料 × 期初/入库/出库/期末（数量+金额），全部取 cost_flow**：期初=period<月累计，入库/出库=当月正/负方向流水（负数取正展示），**期末=期初+入−出（后端构造，守恒关系由此保证）**；仅统计 cost_flow 有流水且物料存在的行（INNER JOIN base_material，孤儿物料流水不计入）；HAVING 过滤期初与收发全 0 的静默行；含全列合计行 |
| 5 | `GET /report/inventory/sluggish?days=90` | 现存量>0 且 **days 天无出库方向流水**（最后出库 DATEDIFF≥days；**从未出库视为呆滞**）；物料粒度跨仓合计；库龄锚点=最后一次入库流水；金额=现存量×加权价（回退 ref_cost）；占比=物料呆滞金额/呆滞合计（4 位）；days≤0 → 参数错误 |
| 6 | `GET /report/production/execution?from&to` | 工单行：计划量/完工量/在制=max(计划−完工,0)/完工率=完工/计划×100（2 位）/超期=**status∉{FINISHED,CLOSED} 且 plan_end_date<CURDATE()**；区间锚点=COALESCE(plan_start_date, DATE(create_time))；超期优先排序，状态转中文 |
| 7 | `GET /report/production/workhours?from&to&workCenterId&groupBy=operation\|employee` | mrp_work_report 按报工时间(report_time)落期；工序维度 JOIN mrp_operation（含报工员工数），员工维度（含涉及工序数）；合格数/不良数/不良率/工时（分钟合计 + 小时=分钟/60 两位）；工作中心过滤经 mrp_wo_step.work_center_id（EXISTS 子查询） |
| 8 | `GET /report/production/material-loss?from&to` | 工单×领料物料：**应耗=已审核领料单明细 should_qty（BOM 应耗×领料倍数）合计，实耗=actual_qty 合计**；超耗=实耗−应耗（负数为节约）；超耗率=超耗/应耗×100（2 位）；区间按领料单 create_time 落期；**应耗=0 或无领料明细的工单 HAVING 跳过**（无法计算超耗率）；含合计行 |
| 9 | `GET /report/finance/expense?period=yyyy-MM` | **部门×科目借方发生额**：POSTED 凭证分录，科目=direction='DEBIT' 且 category∈{COST,PROFIT_LOSS}（5001/5101/6401/6402/6601/6602/6603/6711/6801）；**部门=凭证制单人(create_by)→sys_user.dept_id→sys_dept**（fin_voucher 无部门列的口径降级，无部门归「未分配部门」）；占比=行金额/合计 |
| 10 | `GET /report/hr/piecewage?period=yyyy-MM` | hr_piece_wage(period) JOIN hr_piece_wage_detail JOIN hr_employee：员工×工序的计件产量 qty、工资 amount、工单数 COUNT(DISTINCT work_order_id)；**期间无工资表记录 → 空列表**（需先在人事模块生成期间工资）；含合计行 |

权限：新增 6 个权限码 `report:monthly:list / report:margin:list / report:supplier:list /
report:stock:list / report:production:list / report:hr:list`（销售毛利挂 sales 控制器但独立权限码），
sys_menu 已按 331-335 行同款补 6 行 type=2 按钮数据（component=report/center/ReportCenter；
前端 MainLayout 菜单为硬编码，sys_menu 行仅服务鉴权，不影响侧边栏）。

### 6.3 验证记录（隔离环境 erp_db_report2 + 端口 18084 + Redis db3）

> 本轮 8080 共享实例被并行模块长时间占用且未恢复，沿用 v1.0 先例起隔离实例验证
> （mysqldump 快照 erp_db_report2；Flyway 对已有 history 无操作）。全量 `mvn package -DskipTests`
> 首次失败于并行任务正在编写的 erp-platform-notify（ChannelVO 缺失），等待其落盘后重试通过。

**编译/构建**：`mvn -pl erp-biz/erp-biz-report -am compile` 通过；全量 `mvn package -DskipTests` 通过；
`npx tsc --noEmit` 0 错误；`npx vite build` 通过。

**10 张报表 curl vs 手工 SQL 核对（逐张）**：

| 报表 | 关键数字（API = 手工 SQL） |
|---|---|
| 1 经营月报 2026-09 | 完工工单数 5=5、产量 50=50、销量 344=344、销售额 50520=50520（头表口径；join 明细的手工 SQL 因主子一对多翻倍为 60020，属核对 SQL 写法错误，API 正确）、采购额 36385.20=36385.20、营收 37535.44=6001 贷方合计、成本 24090=6401 借方合计、费用 3500=6601 借 1400+6602 借 2100、毛利率 35.82%=(37535.44−24090)/37535.44、应收 85846/应付 87976.20=时点快照 |
| 2 销售毛利 | 物料 DEMO-WL-0301：49/57820/36750（毛利率 36.44%）；全表合计 619/201345/116193.97 = 手工 SUM(COALESCE(actual_cost, qty×ref_cost)) 一致；**物料维度合计=客户维度合计**（619/201345/116193.97）；7/45 行 actual_cost 为 NULL 走 ref_cost 回退（两口径均被覆盖） |
| 3 供应商绩效 | 7 行与手工 SQL 全列一致：DEMO-GYS-006 订单 6/67595/入库 124270/到货 12 行 10 及时 83.33%/无质检 N/A；DEMO-GYS-001 合格率 0%（60 抽 0 合）；GYS-TEST 16/14648.80/38240.20/29 行 27 及时 93.10%/5340 抽 3325 合 62.27% |
| 4 收发存月报 | **守恒断言：2026-09 全部 37 行、2026-08 全部 28 行 期初+入−出=期末（数量与金额）0 违例**；期末与「period≤月流水累计和」逐物料核对 37/37 一致；2026-08 期末(148069/277612.30)=2026-09 期初（跨月结转一致）；合计行守恒成立 |
| 5 呆滞物料 | days=90 → 6 行/50040.50，逐行=手工 SQL（DEMO-WL-0101 8510/38720.50、WL-0112 120/4080、WL-0108 8000/4000、WL-0111 150/3240，含 2 条从未出库 NULL 物料孤儿行）；days=7 → 15 行（参数生效）；days=-5 → 参数错误 |
| 6 订单执行跟踪 | 18 行、超期 1 张 = 手工 SQL（MO202609010001 生产中 30 计划/20 完工/在制 10/完工率 66.67%/计划完工 09-04<今天）；FINISHED 工单完工率 100% |
| 7 报工工时 | 工序维度 4 行与手工 SQL 一致（校直动平衡 210 合格/2 不良/5239 分钟=87.32h 等）；员工维度（2026-09-01~07）3 行（周建国 2 工序 30/0/959 分钟…）；workCenterId 过滤后仅 1 行（与手工 SQL 同） |
| 8 材料损耗 | 65 行；MO202604100001×DEMO-WL-0201 应耗 10.2/实耗 10/超耗 −0.2/−1.96%；合计 3046.72/3036.00/−10.72/−0.35% = 手工 SQL 一致 |
| 9 费用分析 2026-09 | 3 行（财务部 6401 24090/6602 2100/6601 1400）合计 27590 = 手工 SQL 一致；占比 0.8731/0.0761/0.0507 |
| 10 计件工资 | 2026-09：1 行（EMP202609050001 张三 冲压成型 110 产量/1375 元/2 工单）=手工 SQL；2026-04：40/155=手工（10×8+30×2.5）；空期间 2099-01 → 空列表 code=0 |

**CSV 导出（10/10）**：`Content-Type: text/csv;charset=UTF-8` + `Content-Disposition: attachment` ✓；
首行中文表头 ✓；BOM(efbbbf)+CRLF ✓；行数与 JSON 一致（月报 1+16、毛利 1+11+合计、供应商 1+7、
收发存 1+37+合计、呆滞 1+6、执行 1+18、工时 1+4、损耗 1+65+合计、费用 1+3+合计、工资 1+1+合计）；
无质检数据合格率输出 `N/A`、数字 `toPlainString` 无科学计数法。

**错误参数**：margin groupBy=dept → `groupBy 仅支持 material/customer`；month=2026-13 / period=abc →
`期间格式应为 yyyy-MM`；sluggish days=-5 → `呆滞天数必须为正整数`（均 code=1000 中文错误）。

**前端**：`npx tsc --noEmit` 0 错误、`npx vite build` 通过；**分组目录 SSR 断言 24/24 ALL PASS**
（vite.ssrLoadModule 加载真实 ReportCenter + react-dom/server renderToString：目录标题、8 分组、
14 报表入口、ant-menu 结构、默认选中经营月报、懒加载占位文案；脚本 `scripts/ssr-report-center.mjs`）。

> 复核补充：验证完成后共享 8080 实例由协调线程以新 jar 重启，10 个新接口在共享实例全部
> 复测通过（行数与隔离环境一致：毛利 9 客户行/供应商 7/收发存 37 且守恒 0 违例/呆滞 6/
> 执行 18/工时 4/损耗 65/费用 3/工资 1，CSV 导出头正常）。隔离实例已停止，
> 验证库 `erp_db_report2` 已于 2026-09-06 清理删除（验证结论见上文，主库为唯一库）。

## 7. 遗留问题与协作事项

1. **router/index.tsx 的 2 行合并**：`reportRoutes` 的 import + `...toRouteChildren(reportRoutes)`
   属共享文件改动（MainLayout 菜单 /report/dashboard、/report/center 已预置），主线程知悉即可；
2. 生产完工数以 update_time 落期近似：工单跨月多次完工时finish_qty现值归属最后一次
   更新月；mrp 模块若后续加 finish_time 列，改一行 SQL 即可精确；
3. 库龄锚点取「最后一次入库流水」：期间内先入后出的库存仍按入库时点计龄（业务通用近似）；
   无任何流水的存量行归入「无入库流水」桶单独提示；
4. 财务汇总凭证口径按 6001/6401 编码前缀匹配：财务模块若调整科目体系需同步；
5. 待办审批数依赖 workflow 模块上线 wf_task（约定列 status/deleted）；上线后自动生效无需改动；
6. 报表接口暂不分页（全量聚合导出场景）；数据量增长后的 LIMIT/流式导出为后续优化点；
7. 验证库 `erp_db_report`（18083 实例）保留作复核证据，可随时 `DROP DATABASE` 清理；
8. （v1.1）经营月报/费用分析的部门与余额维度受源表限制：fin_voucher 无部门列（费用分析按
   制单人部门降级）、fin_receivable/inv_stock 无期间余额表（月报的应收/应付/库存总值为当前
   时点快照，历史月份不回溯）；财务模块若加期间余额表可一行 SQL 精确化；
9. （v1.1）收发存月报以 cost_flow 为唯一数据源：成本模块未记账的物料不出现在月报中
   （与库存现状可能存在口径差，属成本记账覆盖范围问题而非报表问题）；cost_flow 中
   material_id 无对应 base_material 的孤儿流水（演示库存在 2 条）已排除；
10. （v1.1）呆滞/库龄的锚点依赖 inv_movement 流水完整性：期初导入库存（无流水）会归入
   「从未出库/无入库流水」桶，上线数据迁移时需补建期初流水；
11. （v1.1）验证库 `erp_db_report2` 已于 2026-09-06 清理删除（验证结论见上文，主库为唯一库）；原
   `DROP DATABASE` 清理；sys_menu 新增的 6 行权限数据（3532-3537）已落在共享 erp_db；
12. （v1.1）材料损耗「应耗」取领料单 should_qty（含损耗率放大），与按 BOM×完工量重算的
   口径存在差异（领料倍数场景）；如需「净 BOM 应耗」口径需 BOM×finish_qty 重写一条 SQL。
