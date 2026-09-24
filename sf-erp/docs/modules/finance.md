# 财务管理模块（erp-biz-finance）

> 对标金蝶 K3 总账（简化版）：多账套 + 应收应付核销 + 资金账户 + 总账凭证 + **凭证模板引擎（业务单据自动凭证）+ 期间结账** + **三大报表（资产负债/利润/账龄）**。
> 后端模块：`erp-backend/erp-biz-finance`；前端：`erp-web/src/pages/finance/`、`src/api/finance.ts`、`src/router/modules/finance.tsx`（报表中心前端挂载见 §12.5）；SQL：`V501__finance_init.sql`、`V502__finance_multi_book.sql`、`V503__fin_book_tenant_org_columns.sql`、`V504__fin_payment_budget_columns.sql`、`V521__finance_voucher_engine_period.sql`、`V531__fin_report_mapping.sql`。

## 1. 功能范围

| 功能 | 说明 |
|---|---|
| **多账套** | 账套档案 CRUD（编码 BS+流水）、X-Book-Id 请求头切换、科目/凭证/收付款/应收应付/资金账户按账套完全隔离、新账套科目树一键复制、禁删保护 |
| 会计科目 | 科目树（parent_id）维护，预置 37 条标准科目（五大类），分录只允许末级科目；**按账套隔离，编码账套内唯一** |
| 应收管理 | 分页查询（客户/状态/月份/单号/仅未核销）、明细、手工补录；来源为销售出库自动 INSERT |
| 应付管理 | 与应收对称；来源为采购入库自动 INSERT |
| 收款核销 | 收款单选择同一客户多笔应收分配核销，事务内回写 `settled_amount` 与状态、账户余额 +、记流水 |
| 付款核销 | 对称；账户余额不足（不允许透支）整体回滚 |
| 资金账户 | 现金/银行账户 CRUD、余额快照（期初/收入/支出/当前）、流水分页 |
| 总账凭证 | 手工录入（借贷平衡 BigDecimal.compareTo 精确校验）、审核过账（POSTED 禁改禁删）、科目汇总表、试算平衡表 |
| **三大报表** | 资产负债表（期初/期末+勾稽断言）/ 利润表（本月+本年累计）/ 应收应付账龄分桶，科目余额取数、fin_report_mapping 映射驱动（§12） |

## 2. 表结构（V501，11 张表）

| 表 | 说明 | 关键约束 |
|---|---|---|
| fin_subject | 会计科目树 | uk(code)；category 五大类；direction 借/贷 |
| fin_account | 资金账户 | uk(account_no)；current_balance 由条件 UPDATE 原子维护 |
| fin_account_flow | 账户流水 | 每笔收付款自动写入，含 balance_after 快照 |
| fin_receivable | 应收单 | status UNSETTLED/PARTIAL/SETTLED；销售模块直接 INSERT |
| fin_payable | 应付单 | 对称；采购模块直接 INSERT |
| fin_receipt / fin_receipt_item | 收款单主子表 | uk(receipt_no)；子表记录每笔应收核销额 |
| fin_payment / fin_payment_item | 付款单主子表 | 对称 |
| fin_voucher | 凭证主表 | status DRAFT/POSTED；total_debit/total_credit |
| fin_voucher_entry | 凭证分录 | 摘要+科目+借/贷单边金额，末级科目 |

种子数据：37 个标准科目（1001 库存现金、1002 银行存款、1122 应收账款、2202 应付账款、1405 库存商品、6001 主营业务收入、6401 主营业务成本、222101 应交增值税 等）+ 2 个资金账户（库存现金 10000 / 工行基本户 500000）。

## 3. 核销事务（FinReceiptServiceImpl / FinPaymentServiceImpl）

`@Transactional(rollbackFor = Exception.class)` 内依次执行，任一步失败整体回滚：

1. 校验：收款金额 == 明细核销额之和（compareTo）；明细应收同属一个客户；
2. 逐笔条件 UPDATE（`FinReceivableMapper.applyVerify`，XML）：
   `SET settled_amount = settled_amount + ?, status = IF(settled_amount + ? >= amount,'SETTLED','PARTIAL') WHERE ... AND amount - settled_amount >= ?`
   —— 数据库层兜底"核销额 ≤ 未核销余额"，并发不超核销；影响 0 行即抛业务异常；
3. 账户余额条件 UPDATE 原子增减（付款方向带 `current_balance >= amount` 防透支）；
4. 收/付款单主子表落库（单号 SK/FK + yyyyMMdd + 4 位流水，Redis INCR，降级时间戳）；
5. 写账户流水（balance_after 快照）。

## 4. 凭证规则（FinVoucherServiceImpl）

- 分录 ≥ 2 行；每行借/贷互斥（一侧 > 0 另一侧 = 0）；科目必须启用且为末级；
- 借贷平衡用 `BigDecimal.compareTo == 0` 精确比较（禁 equals）；
- 状态机单向 DRAFT → POSTED；过账后修改/删除一律拒绝；
- 科目汇总 / 试算平衡只统计 POSTED 凭证；试算平衡输出六栏（期初/本期/期末 × 借/贷）+ 合计 + balanced 标识（三组合计均 compareTo 相等）。

## 5. 接口清单（统一前缀 /api，JWT 登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | /finance/subjects、PUT/DELETE /{id} | 科目列表（含 leaf）与维护 |
| GET/POST | /finance/accounts、PUT/DELETE /{id} | 账户 CRUD |
| GET | /finance/accounts/{id}/balance | 余额快照（期初/收入/支出/当前） |
| GET | /finance/accounts/{id}/flows | 流水分页 |
| GET | /finance/receivables | 分页：customerId/status/month/keyword/unsettledOnly |
| GET/POST | /finance/receivables/{id}、/finance/receivables | 明细、手工补录 |
| GET | /finance/payables | 分页：supplierId/status/month/keyword/unsettledOnly |
| GET/POST | /finance/payables/{id}、/finance/payables | 明细、手工补录 |
| GET/POST | /finance/receipts、/finance/receipts/{id} | 收款单分页/明细/保存核销（事务） |
| GET/POST | /finance/payments、/finance/payments/{id} | 付款单分页/明细/保存核销（事务） |
| GET/POST/PUT/DELETE | /finance/vouchers(/{id}) | 凭证分页/新增/修改/删除（仅草稿可改删） |
| POST | /finance/vouchers/{id}/audit | 审核过账 |
| GET | /finance/vouchers/summary?subjectId&from&to | 科目汇总（借/贷合计、期初/期末余额） |
| GET | /finance/trial-balance?from&to | 试算平衡表 |

## 6. 并行协作与跨模块约定

- `fin_receivable` / `fin_payable` 本模块为 owner：销售/采购直接 INSERT（customer_id/supplier_id, biz_no, amount, settled_amount=0, status='UNSETTLED'，可选 biz_date/due_date/remark），本模块负责核销回写与查询；
- 客户/供应商名称通过 `PartnerNameLookup` 只读 SELECT `base_customer` / `base_supplier`（不 import 对方代码）。对方表由 basedata V101 建；未落库时自动降级为名称 null（前端显示"客户 #id"），落库后无需改动自动恢复；
- 单号生成器类名 `FinanceBizNoGenerator`（避免与 inventory 同名 Bean 冲突）。

## 7. 前端

- `pages/finance/receivable/ReceivableList.tsx`：筛选 + 列表 + 收款核销 Modal（多笔分配、实时合计）；
- `pages/finance/payable/PayableList.tsx`：对称付款核销；
- `pages/finance/voucher/VoucherList.tsx` + `VoucherFormModal.tsx`：凭证列表/明细/审核/删除 + 录入表单（分录行增删、借贷互斥输入、底部实时借贷平衡条，不平禁止提交）；
- `pages/finance/components/SettleModal.tsx`：收/付款核销共用弹窗；
- 路由：`router/modules/finance.tsx` 导出 `financeRoutes`（path 无首斜杠：finance/receivable、finance/payable、finance/voucher），由主线程合并进 router/index.tsx。

## 8. 验证记录（2026-09-05）

- `mvn -pl erp-finance -am compile -q` 通过；
- Flyway V501 执行通过（含主库 erp_db：V501 success，37 科目 + 2 账户种子正确）；
- `cd erp-web && npx tsc --noEmit` 通过（finance 相关 0 错误）；
- curl 端到端（独立验证库 + 主库 8080 实例）：
  - 手工 INSERT 应收 1000 → 收款核销 400 → `PARTIAL`（settled=400）；
  - 超额核销 2000>600 被拒；金额与分配不一致被拒；
  - 再核销 600 → `SETTLED`，现金账户 10000→11000，流水 balance_after 正确；
  - 应付 500 付款核销 → `SETTLED`，银行账户 500000→499500；余额不足付款整体回滚（应付仍 UNSETTLED）；
  - 借贷不平凭证（100/90）被拒；单行借贷同填被拒；非末级科目被拒；
  - 平衡凭证保存→审核过账→修改/删除均被拒；科目汇总（1001 贷方 1000）与试算平衡（balanced=true）正确。

## 9. 多账套（V502/V503，对标金蝶多账套）

### 9.1 数据模型

- `fin_book` 账套档案：`code`（BS+流水，BS1001/BS1002…，系统生成不可改）/ `name` / `period_policy`（当前仅 NATURAL_MONTH 自然月，预留扩展）/ `currency`（本位币 ISO，默认 CNY）/ `is_default`（默认账套标记，不可删）/ `status` / `remark`；含 tenant_id/org_id（租户级共享档案，org=0，与 V1012 对齐，V503 补列）。
- 11 张 fin 业务表（subject/account/account_flow/receivable/payable/receipt(+item)/payment(+item)/voucher(+entry)）全部增加 `book_id BIGINT NOT NULL DEFAULT 1001`（幂等 ALTER，存量数据归 1001 主账套；外部模块裸 INSERT 不带 book_id 时落默认账套）。
- 唯一键按账套重建：`fin_subject uk_code(code) → (book_id, code)`、`fin_account uk_account_no → (book_id, account_no)`——不同账套各自维护科目树/资金账户，编码可重复；凭证/收付款单号由全局 Redis 计数生成天然不重，唯一键不动。
- 种子：1001 主账套（is_default=1，存量数据归它）+ 1002 测试账套（空，科目树需复制或手工建立）。

### 9.2 上下文与隔离设计（为什么不用 MP 租户插件）

- `BookContextInterceptor`（仅拦 `/finance/**`）解析请求头 **`X-Book-Id`** → 校验账套存在 → 存 `BookContext` ThreadLocal（缺省 1001），并在**响应头回显**实际生效账套；afterCompletion 清理防线程串号。非数字/账套不存在直接报业务错。
- **隔离在 Service 层显式实现**而非 MP TenantLine 插件：`MybatisPlusInterceptor` Bean 在 erp-framework（共享文件不可改），且显式条件对手写 XML/JOIN 行为完全可预测。具体：所有查询 `eq(bookId)`、所有写入 `setBookId(BookContext.getBookId())`、手写 XML（applyVerify/changeBalance/sumAmountByDirection/科目汇总/试算平衡）显式加 `book_id = #{bookId}` 参数。
- **横向越权防护**：`BookContext.checkBookOwnership` —— 按 ID 取到的实体若不属于当前账套，视同"数据不存在"（凭证/科目/账户/应收应付/收付款单的 detail/update/delete/audit 均校验）；凭证分录科目、核销的账户/应收应付也必须属于当前账套。
- 账套与租户/组织正交：tenant/org 由共享框架插件自动追加条件，book_id 在其上再按账套细分。

### 9.3 账套接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | /finance/books、PUT/DELETE /{id} | 账套 CRUD（编码自动生成；默认账套/已有凭证的账套禁删） |
| POST | /finance/books/{id}/copy-subjects?fromBookId=1001 | 复制科目树到目标账套（按层级逐级复制并重挂 parent_id，幂等：同编码跳过） |

典型初始化流：新建账套 → `POST /finance/books/{id}/copy-subjects`（或手工建科目）→ （可选）建资金账户 → 记凭证/收付款。

### 9.4 前端

- `pages/finance/books/BookList.tsx`：账套列表 + CRUD + 当前账套徽标 + 「复制科目」；路由 `finance/books`（`router/modules/finance.tsx` 追加，菜单入口由主线程挂）。
- `pages/finance/components/BookSelect.tsx`：账套切换下拉，放在应收/应付/凭证/账套管理各页筛选栏（页内切换，不动全局布局）；停用账套置灰。
- `api/finance.ts`：模块级 `currentBookId` + `setFinanceBookId()`；财务专用 axios 实例（与共享 request.ts 行为一致）自动附带 `X-Book-Id` 头——不改共享文件，不影响其他模块。切换账套即 `invalidateQueries(['finance'])`，弹窗类查询 key 均带账套 ID 防跨账套串缓存。

## 10. 验证记录（多账套，2026-09-06）

- `mvn -pl erp-biz-finance -am compile` 与全量 `mvn package` 通过；`npx tsc --noEmit` finance 相关 0 错误；
- Flyway V502/V503 执行成功；两脚本对库重复执行（幂等重跑）均无错误；
- curl 端到端（admin）：
  - 无 X-Book-Id → 默认 1001（响应头回显 X-Book-Id: 1001）；`X-Book-Id: abc` → 400；`X-Book-Id: 9999` → 账套不存在；
  - 1001 科目/凭证可见；X-Book-Id: 1002 科目 0 条（空账套）→ copy-subjects 复制 38 条（重跑复制 0 条，幂等；parent_id 重挂正确：222101→新 2221）；
  - 1002 做凭证：借贷平衡（500/500）保存→审核过账；借贷不平（100/90）被拒；分录引用 1001 科目被拒（"不属于当前账套"）；
  - 隔离：1002 凭证列表仅见新凭证，1001 凭证/科目汇总/试算平衡不含 1002 数据（1002 试算平衡 500/500 balanced=true；1001 1234.56/1234.56）；跨账套读 1002 凭证（X-Book-Id:1001）被拒；
  - 收付款/应收应付：1002 手工应收 300 → 收款核销 300 → SETTLED、1002 账户 1000→1300、流水正确；1001 同编号账户（CASH-001）余额/收入不受影响；同 account_no/CASH-001 在 1002 可重复创建（账套内唯一）；
  - 删保护：删 1001（默认账套）被拒；删 1002（已有 1 张凭证）被拒；新建空账套（BS1003 自动编码）可正常增/改/删；
  - 账套内科目编码重复被拒，同编码跨账套可并存（uk(book_id, code) 生效）。

## 11. 预算控制接线（finance → budget，V504，2026-09-06）

付款单创建（即核销执行入口）接入预算控制，依赖方向 finance → budget 单向
（erp-biz-finance pom 增加 erp-biz-budget；budget 仅依赖 framework+basedata，无循环）：

- **V504 迁移**：`fin_payment` 幂等补 `dept_id BIGINT NULL`（费用归属部门）+
  `subject_id BIGINT NULL`（预算科目）；Form/VO 透传两字段，实体映射，为空兼容老数据。
- **先校验**（`FinPaymentServiceImpl.create` 步骤 1.5，核销/扣款前）：
  归属定位 = 表单 `deptId+subjectId` 显式优先；deptId 空则按「供应商 → 最近采购单
  （pur_order）/最近应付单创建人 → sys_user.dept_id」推导（`BudgetIntegrationMapper`
  只读直查约定表，pur_order 缺失降级）；科目或部门定位不到 → 跳过预算校验。
  调 `BudgetControlService.check(dept, subject, 年, 月, 付款金额)`：
  - **BLOCK** 超预算 → BusinessException，付款整体拒绝（应付状态/账户余额/单据均零变动）；
  - **WARN** 超预算 → 放行，remark 追加「⚠超预算：占用率xx%」（列宽 200 截断）+ Service log.warn。
- **后推数**（付款落库成功后同一事务）：调 `BudgetControlService.record`
  （sourceType=PAYMENT，sourceId=付款单 id、sourceNo=FK 单号），预算执行分析/占用校验
  立即可见；付款回滚则登记一并回滚。
- 实测：BLOCK 方案（预算 1000）付款 1200 被拒；改 WARN 后成功且 remark 带超预算提示；
  budget_usage_record 生成 PAYMENT 行；analysis 执行率 120.00%；无 dept/subject 老数据
  付款正常（跳过预算，日志留痕）。

## 12. 财务三大报表：资产负债表 / 利润表 / 应收应付账龄（V531，2026-09-06）

从**科目余额**取数的报表深化（财务深度的试金石）。后端仍在 erp-biz-finance
（`FinReportController` / `FinReportService(Impl)`），前端挂在报表中心财务分组
（4 个新 Pane，见 report.md §4 的 ReportCenter 财务组）。

### 12.1 取数映射表 fin_report_mapping（V531）

报表行项目 ↔ 科目编码**全部做成配置**（避免硬编码，科目体系变化改配置即可）：

| 列 | 说明 |
|---|---|
| report_type | BALANCE_SHEET / INCOME_STATEMENT |
| side | 行项目所属侧：ASSET / LIABILITY / EQUITY（利润表留空） |
| item_name | 行项目名称（含「减：」「一、」展示前缀） |
| subject_codes | 科目编码集合（逗号分隔，**前缀匹配**：2221 命中 2221+222101；空=无科目行显示 0，如在建工程/税金及附加） |
| direction | 取数方向：DEBIT=借方净额(借−贷) / CREDIT=贷方净额(贷−借) |
| calc_type | 计算行：ASSET_TOTAL / LIAB_TOTAL / EQUITY_TOTAL / LIAB_EQ_TOTAL / OPERATING_PROFIT / TOTAL_PROFIT / NET_PROFIT（=上方数据行运行合计），空=普通取数行 |
| include_pl | 1=未分配利润行并入损益类科目未结转净额 |
| book_id | 0=全账套通用（映射按编码对齐，账套复制科目树后同样生效） |

种子 36 行（资产负债 23 + 利润 13），关键口径：
- **存货 = 1403+1405+5001+5101**（原材料+库存商品+生产成本/制造费用=在产品，
  成本类科目并入存货，否则资产必不平衡）；
- **减项科目**（累计折旧 1602 / 坏账准备 1231）在资产侧按借−贷取数，贷方余额自然为负；
- **未分配利润 = 4103+4104 余额 + 损益类(PROFIT_LOSS)科目未结转净额**（include_pl），
  损益期末若有余额则 `profitLossNotCarried=true` 并在 warnings 标注"按现行余额列示"；
- **营业收入 = 6001+6051、营业成本 = 6401+6402**（主营业+他业务合并列示）。

### 12.2 取数与勾稽原理

- 余额唯一来源 = **POSTED 凭证分录**聚合（复用试算平衡的
  `FinQueryMapper.selectTrialBalanceRows`，一次查询得每科目期初借/贷+本期借/贷；
  本系统无独立余额表，亦无凭证外期初，故任意方向净额可精确推导）；
- 期初 = 期间前累计；期末 = 期初 + 本期净发生；
- **勾稽断言**：资产总计 −（负债合计+所有者权益合计）= diff，|diff|≤0.01 才
  `balanced=true`（期初列同理 openingDiff）。因映射覆盖全部科目类
  （资产+成本→资产侧，负债→负债侧，权益+损益→权益侧未分配利润），借贷必平；
- **防呆提示**：期末余额非 0 的科目若未被任何映射行覆盖（或被多行重叠），
  输出到 `warnings`（不平时的排查线索）；利润表对损益科目做同样覆盖检查。

### 12.3 接口（权限码 finance:report:list，sys_menu id=3551）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /finance/reports/balance-sheet?period=yyyy-MM&bookId= | 资产负债表：期初/期末两列、两侧对照、balanced/diff/openingDiff、warnings；`&export=csv` 附件 |
| GET | /finance/reports/income-statement?period&from&to&bookId= | 利润表：本月数+本年累计数（from/to 显式传入时本月列=[from,to] 自定义区间，累计=该年年初至 to） |
| GET | /finance/reports/ar-aging?asOf=yyyy-MM-dd&bookId= | 应收账龄：客户×分桶 1-30/31-60/61-90/91-180/180+ 天 + 汇总行 + 占比 |
| GET | /finance/reports/ap-aging?asOf&bookId= | 应付账龄（供应商，与应收对称） |

- `bookId` 参数显式传入时优先于 X-Book-Id 请求头（供报表中心 CSV 下载等
  无法携带请求头的场景），不存在报"账套不存在"；
- 账龄口径：未结算余额 = amount − settled_amount；**账龄锚点 = 单据日期
  biz_date（缺失回退 DATE(create_time)）**——due_date 列存在但覆盖率不足
  （应收 30/46、应付 26/53）且需求指定单据日期口径，故未使用到期日口径；
  asOf 之后立据的单据不计入（未来单据不属于该时点存量）；首桶含当日（0-30 天）；
  分桶金额合计恒等于未结算总额（守恒）。

### 12.4 验证记录（隔离环境 erp_db_fin + 端口 18085 + Redis db4，2026-09-06）

- `mvn -pl erp-biz/erp-biz-finance,erp-biz/erp-biz-report -am compile` 通过、
  全量 `package -DskipTests` 通过；`npx tsc --noEmit` 0 错误、`npx vite build` 通过；
- Flyway V531 执行成功，脚本对已应用库重复执行无错误（幂等）；
- **资产负债表 vs 手工 SQL 逐行核对一致**（2026-09，账套 1001）：
  - 期末：货币资金 −29,365.44（现金/银行贷方余额，演示数据）、应收 169,774.33、
    存货 −100,362.50、固定资产 926,000、减累计折旧 −17,434.16 →
    **资产总计 948,612.23**；应付 926,000 → 负债合计 926,000；
    未分配利润 22,612.23（=损益未结转净额）→ **负债+权益 948,612.23**；
    `balanced=true，diff=0.00，openingDiff=0.00`；
  - 期初：资产 937,666.79 = 负债 925,000 + 权益 12,666.79；
  - 2026-08 期末与手工 SQL 一致（937,666.79 = 925,000 + 12,666.79），
    且 = 2026-09 期初（跨期结转一致）；warnings 如实提示
    "损益类科目期末净额 22612.23 未结转，已按现行余额并入未分配利润列示"；
  - 空账套 1002：全 0 行 balanced=true；bookId=9999 → 账套不存在；
    period=2026-13 → 期间格式错误；
- **利润表 vs 手工 SQL 一致**（2026-09）：本月营收 37,535.44（6001 贷）/成本 24,090（6401 借）/
  销售费 1,400/管理费 2,100 → 营业利润=利润总额=**净利润 9,945.44**；
  本年累计 171,008.89/112,730/12,600/23,066.66 → **22,612.23**
  （=资产负债表未分配利润期末，三表勾稽闭环；与报表中心经营月报/财务汇总的
  6001/6401 凭证口径同数）；自定义区间 2026-08-15~09-20 营收 85,393.85 正确；
  from>to 被拒；
- **账龄 vs 手工 SQL 分桶逐桶一致**（asOf=2026-09-30）：
  应收 88,347.00/23 单/9 客户，桶 49,737/38,080/0/530/0（占比 0.563/0.431/0/0.006/0），
  TOP=宁波海天 19,180…；应付 218,958.69/76 单，桶 55,866.54/56,953.23/35,902.26→35,590.26/
  70,548.66/0；名称缺失客户降级显示 `#id`；分桶合计=未结算总额（守恒）；
- CSV（4/4）：`text/csv;charset=UTF-8` + attachment + BOM + CRLF + RFC4180 转义
  （`"6001,6051"` 加引号）+ 中文表头；资产负债表 1+23+4 行（含勾稽差额/是否平衡/口径提示）、
  利润表 1+13+1 行、账龄 1+客户行+合计+分桶占比行；
- 前端：`node scripts/ssr-report-center.mjs` 32/32 ALL PASS（18 报表入口 +
  4 个新 Pane 真实 SSR 渲染；脚本补了 Node 环境 localStorage polyfill 与
  QueryClientProvider 包裹——并行任务 i18n 模块在顶层读 localStorage）。

### 12.5 前端挂载（报表中心财务分组）

| 文件 | 说明 |
|---|---|
| `src/pages/report/center/BalanceSheetPane.tsx` | 资产/负债权益**两列对照表**（期初+期末 ×2）+ 平衡校验标签（绿"勾稽平衡"/红"差额"）+ 损益未结转橙标 + warnings Alert + CSV |
| `src/pages/report/center/IncomeStatementPane.tsx` | 月份 + 自定义区间 RangePicker + 项目/本月数/本年累计数表（小计行加粗着色）+ 取数科目列 + CSV |
| `src/pages/report/center/ArAgingPane.tsx` / `ApAgingPane.tsx` | asOf 日期 + 分桶统计卡（金额+占比+基色）+ 客商×分桶表（**分桶色阶**：金额越深、桶越老越红）+ 合计行 + 守恒断言文案 + CSV |
| `src/pages/report/center/FinAgingShared.tsx` | 应收/应付共用渲染（统计卡 + 色阶表） |
| `src/api/finance.ts` | 4 个接口类型与请求（走财务 axios 实例自动带 X-Book-Id）；CSV 复用 report.ts `downloadReportCsv`（显式传 bookId 参数） |
| ReportCenter.tsx 财务组 | 财务汇总 / 费用分析 / **资产负债表 / 利润表 / 应收账龄分析 / 应付账龄分析**（6 项） |

## 13. 业务财务一体化：凭证模板引擎（V521，2026-09-06）

**任一业务单据审核后按凭证模板自动生成会计凭证（DRAFT）**，对标金蝶"业务凭证"。
SQL：`V521__finance_voucher_engine_period.sql`；后端 `VoucherEngine*` /
`VoucherTemplate*`；扫描器 `VoucherEngineScheduleConfig`（与 WMS 扫描器同一模式，
零侵入业务模块：只读约定表，不 import 采购/销售/发票代码）。

### 13.1 表与模板种子

| 表 | 说明 |
|---|---|
| fin_voucher_template | 分录规则模板：code/name/biz_type/entry_rules(JSON)/enabled/priority；**uk(biz_type)** 每类一条；全局共享（不按账套），科目编码生成时按目标账套解析 |
| fin_voucher 补列 | source_type/source_id 来源列（NULL=手工凭证）+ **uk_voucher_source(book_id,source_type,source_id)** DB 级幂等 |

entry_rules JSON 数组元素：`{"side":"DEBIT|CREDIT","subjectCode":"1405|@account","amountExpr":"order_total|detail_tax_excl_total|tax_total|detail_cost_total","summary":"采购入库 {单号} ..."}`；
摘要变量 `{单号}/{日期}/{类型}`；`@account` 按单据资金账户类型解析（CASH→1001 / BANK→1002）。

**八类标准模板（科目金额口径表）**——暂估链路：入库按不含税暂估，发票到达补税差，
应付/应收最终=价税合计：

| biz_type | 分录（模板种子） | 金额来源口径 |
|---|---|---|
| PUR_INSTOCK 采购入库 | 借1405 贷2202（各=不含税合计） | detail_tax_excl_total=Σround(明细amount÷(1+税率%))——pur 明细 price 为**含税**单价 |
| PUR_RET 采购退货 | 同上红字（引擎取负） | 同上；pur_return(_detail) 同构 |
| SAL_OUTSTOCK 销售出库 | 借1122 贷6001（各=不含税合计）+ 借6401 贷1405（成本） | order_total=sal 明细合计（price 为**不含税**，与发票模块口径一致）；detail_cost_total=Σactual_cost（V2101 快照，NULL 计 0，全 0 跳过成本腿） |
| SAL_RET 销售退货 | 同上红字（引擎取负） | 成本=按退货数量分摊原出库行 actual_cost：round(原行cost×退货qty÷原行qty,2) |
| INVOICE_SALES 销项发票 | 借1122 贷222101（各=税额） | tax_total=发票 tax_amount（红字票自带负号=自动红字） |
| INVOICE_PURCHASE 进项发票 | 借222101 贷2202（各=税额） | 同上；凭证日期优先认证日（进项税额归属期） |
| RECEIPT 收款 | 借@account 贷1122（各=收款额） | order_total=fin_receipt.amount；@account 按账户类型→1001/1002 |
| PAYMENT 付款 | 借2202 贷@account（各=付款额） | 同上 |

### 13.2 引擎（VoucherEngineService）

- `generate(bizType,bizId)`：读单据主子表→按模板组装（金额 BigDecimal HALF_UP 2 位、
  摘要变量替换、**同科目+方向合并**、0 金额腿剔除）→**借贷平衡校验**（|差|>0.01 抛异常
  不落库；0<|差|≤0.01 尾差调整到绝对值最大的一腿）→DRAFT 落库；
- **幂等三层**：应用层查 source 定位已生成跳过 → 事务内复查 → uk_voucher_source 唯一键兜底；
  **软删旧凭证仍占用唯一键**——撞键时若占用行为 deleted=1，释放其 source 列后重试
  （"删除自动凭证后重扫重新生成"可行）；
- **红冲联动**：SAL_RET/PUR_RET 引擎取负（负数分录）；发票红字票金额列自带负号；
- 期间已结账的单据不生成（assertPeriodOpen，见 §14）；
- **扫描器**：`@Scheduled` 30s（fixedDelay）扫八类已生效且无对应凭证的单据，单张失败仅
  warn 不中断；生效口径=出入库/退货 status=10、发票 status=10（已开票/已认证，含红字）、
  收付款落库即生效。开关 `biz.voucher.auto.enabled`（默认 true）、目标账套
  `biz.voucher.auto.book-id`（默认 1001，调度线程显式 set/clear BookContext）、
  回看窗口 `biz.voucher.auto.days-back`（默认 7 天，历史补录走手动接口）。

### 13.3 接口（权限码 finance:voucherTemplate:\*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | /finance/voucher-templates、PUT/DELETE /{id} | 模板 CRUD（entry_rules 结构校验；bizType 不可改；enabled=0 停用即不再生成） |
| GET | /finance/voucher-engine/preview?bizType=&bizId= | 试算预览（不落库、不占单号，与实际生成同构；返回 WILL_GENERATE/ALREADY_GENERATED/TEMPLATE_DISABLED/NO_TEMPLATE） |
| POST | /finance/voucher-engine/scan?bizType=&from=&to= | 手动触发扫描（当前账套；补扫历史单据用 from/to 圈定业务日期） |

菜单/权限种子（V521）：274 凭证模板（finance/voucher-templates）+2741~2743 按钮、
275 会计期间（finance/periods）+2751 结账/反结账；**前端模板管理页未做（仅后端接口，
MainLayout 菜单由主线程挂）**。

### 13.4 实测记录（2026-09-06，主库 8080）

- 八类全部实测：采购入库 RK...0008（明细 1500/13% + 110/13%）→ 借1405=贷2202=**1424.78**
  （手算 1327.43+97.35 一致）；销售出库 XS...0007 → 借1122=贷6001=1300 + 借6401=贷1405=412.06
  （=Σactual_cost 267.37+144.69）；销项蓝票 借1122=贷222101=1357.52；红字销项票
  借1122=贷222101=-130（负数分录）；销售退货 借1122/6401、贷6001/1405 全负
  （收入-160、成本-303.16=原行分摊）；采购退货 借1405=贷2202=-66.37；收/付款 @account
  正确落到 1001/1002；
- 幂等：全量重扫 0 新增；删除自动凭证后（模板停用期间）扫描 0 生成 + preview=TEMPLATE_DISABLED；
  重新启用后扫描恢复生成（含软删唯一键让位）；扫描器 30s 自动轮询自动追平并行任务新造单据；
- 平衡断言：全库 fin_voucher 按 voucher 分录汇总 **借=贷 0 张不平**（存量+新生成 160+ 张）；
  主表 total_debit/total_credit 与分录汇总 0 偏差；7 天窗口剩余候选 0。

## 14. 总账期间结账/反结账（V521）

- 表 `fin_period`：period(yyyyMM)+book_id（uk 按账套）+status OPEN/CLOSED+结账人/时间+
  反结账人/时间；**与 erp-cost 的 cost_period（成本期间）相互独立**；
- 期间判定=凭证日期所在自然月；`POST /finance/periods/{period}/close`：该期存在 DRAFT
  凭证拒绝（提示数量）→ 全部 POSTED 才允许 CLOSED；`POST /{period}/reopen` 反结账
  （权限 finance:period:audit，OperLog 留痕）；`GET /finance/periods` 期间列表
  （凭证数/未过账数/结账信息，期间倒序）；
- **期间控制**：凭证新增/修改/过账/删除四入口（FinVoucherServiceImpl）调用
  `FinPeriodService.assertPeriodOpen`——所属期间 CLOSED 即拒绝（修改校验前后两个期间）；
  凭证引擎生成同样受控；
- 前端 `pages/finance/periods/PeriodList.tsx`（列表+结账/反结账 Popconfirm+未过账数提示，
  账套切换）+ 路由 `finance/periods`；**MainLayout 菜单路径 finance/periods 由主线程挂**；
- 实测（账套 1002 隔离验证）：DRAFT 存在→close 拒绝（提示 1 张）→过账→close 成功→
  该期新增凭证被拒（"会计期间[202608]已结账，禁止新增凭证"）→修改凭证日期跨入已结账期被拒→
  reopen→新增成功；重复结账/重复反结账/未结账反结账/非法格式（2026-08）均正确拒绝；
  OperLog 记录结账/反结账操作。

## 15. 遗留问题

- `fin_voucher.audit_by` 当前未写入审核人（JWT 用户上下文待 framework 提供 SecurityUtils 后接入，MP 审计字段 update_by 已自动记录操作人）；
- ~~收款/付款单暂未自动生成总账凭证~~ → V521 凭证模板引擎已覆盖（RECEIPT/PAYMENT 模板，§13）；
- ~~凭证无期间（会计月）结账/反结账控制~~ → V521 总账期间已上线（§14；损益结转见下条）；
- （三大报表 §12 已知口径限制第 1 条）损益类科目期末结转利润仍未做：期间 CLOSE 目前只锁
  凭证不做结转分录（借收入贷本年利润），上线结转后资产负债表「未分配利润」行自动收敛；
- 账套删除保护目前只校验"默认账套 + 有凭证"（按需求），科目/账户/应收应付残留数据的账套仍可删（孤儿数据不影响查询，因 book_id 过滤）；
- `fin_book` 命中 DataScope 的 `fin_` 前缀组织隔离规则：org 范围非 ALL/超管的用户查询账套列表可能被注入 org_id 条件而看不到 org=0 的账套（管理员不受影响）；建议主线程在 framework 的 `ORG_SCOPED_EXCLUDES`/`TENANT_SHARED_TABLES` 登记 `fin_book`（一行改动，本模块不可改共享文件）；
- 账套停用（status=0）仅前端切换列表置灰，后端未拦截停用账套的记账操作（可后续在拦截器/服务层加校验）；
- （三大报表，§12）**已知口径限制**：
  - 损益类科目无结账机制，期末余额未结转为 0 时按现行余额并入「未分配利润」并在
    warnings 标注（与金蝶"未结账当期利润滚存"口径一致；期末结账上线后此行自动收敛为
    本年利润科目余额）；
  - 账龄锚点为**单据日期**（biz_date 缺失回退 create_time），未使用 due_date 到期日口径
    （列存在但覆盖率不足半数且需求指定单据日期口径）；需按"逾期天数"口径时改一行 SQL；
  - fin_report_mapping 命中 DataScope 的 `fin_` 前缀规则与 `fin_book` 同款限制：
    org 范围非 ALL/超管的用户可能查不到 org=0 的映射行（管理员不受影响，登记
    ORG_SCOPED_EXCLUDES 即可修复）；
  - 报表按月（period）驱动，无"上年同期"对比列；资产负债表未拆流动/非流动小计
    （种子映射加行即可，引擎已支持运行合计）；
  - 验证库 `erp_db_fin` 已于 2026-09-06 清理删除（验证结论见上文，主库为唯一库）。

