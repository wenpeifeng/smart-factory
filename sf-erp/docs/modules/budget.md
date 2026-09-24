# 预算管理模块（erp-biz-budget）设计说明

> 模块：erp-backend/erp-biz-budget + erp-web/src/pages/budget
> SQL：script/sql/migration/V971__budget_init.sql + V972__budget_tenant_org_columns.sql（版本段 V97x）
> 状态：开发完成（预算编制矩阵/下达锁定/控制校验/执行分析全链路 curl 实测通过）；
> 付款两段式接线已落地（finance→budget 单向依赖，V504，实测见 §5）

## 1. 功能范围（对标金蝶预算管理）

| 功能 | 表 | 说明 |
|---|---|---|
| 预算方案 | budget_plan | 方案编码/名称/年度/状态机 草稿→已下达→已关闭/编制人；总额冗余列随矩阵保存重算 |
| 预算明细 | budget_item | 方案×部门(sys_dept)×科目(fin_subject 末级费用/成本类)×月份(1-12) 金额；uk 四元组 |
| 预算控制配置 | budget_control_config | 每方案一条：控制方式 WARN警告放行/BLOCK阻止 + 超预算阈值%（默认 100） |
| 预算执行数登记 | budget_usage_record | 付款等场景推式记账（付款接线已落地：fin_payment V504 已有 dept_id/subject_id，见 §5） |
| 执行分析 | （只读统计） | 部门×科目 预算/已用/余额/执行率 + 月度执行曲线 |

## 2. 状态机与口径

- **方案**：`DRAFT 草稿 → RELEASED 已下达 → CLOSED 已关闭`，单向流转。
  仅 DRAFT 可编辑/删除/编制明细（下达后矩阵只读）；下达要求至少 1 条明细；
  关闭仅 RELEASED；控制配置 DRAFT/RELEASED 可调、CLOSED 禁改。
- **编制科目口径**：仅【末级费用/成本类】科目 —— `fin_subject.direction='DEBIT'`
  且 `category IN ('COST','PROFIT_LOSS')` 且无在用子科目。
- **明细保存**：矩阵整体提交；单元格 upsert（`INSERT ... ON DUPLICATE KEY UPDATE`
  顺带复活逻辑删行，避开 uk(plan,dept,subject,month) 与逻辑删冲突），
  未提交的部门×科目组合逻辑删；保存后重算 `budget_plan.total_amount`。
- **已用预算口径（控制与分析一致）**：`已用 = 凭证口径 + 登记口径`
  - 凭证口径（约定表只读）：`fin_voucher(POSTED) × fin_voucher_entry(该科目 debit_amount>0)`,
    部门归属 = 制单人 `sys_user.dept_id`（fin_voucher 无部门列的近似口径）；
  - 登记口径（本模块表）：`budget_usage_record` 求和（PAYMENT/MANUAL，冲销负数自然抵减）。
- **执行率**：已用/预算×100 保留 2 位；>100% 前端标红；预算为 0 显示 `-`。

## 3. BudgetControlService 对外签名（跨模块 API，稳定约定）

接口：`com.erp.budget.service.BudgetControlService`（实现 `BudgetControlServiceImpl`）。

```java
/** 付款/费用发生前校验：超预算按方案配置 WARN 放行或 BLOCK 抛 BusinessException */
BudgetCheckResult check(Long deptId, Long subjectId, Integer year, Integer month, BigDecimal amount);

/** 登记预算执行数（付款单创建/审核时推式记账；红冲/退款传负数金额） */
Long record(BudgetUsageRecordForm form);

/** 查询已用预算（凭证口径 + 登记口径合计，只读不校验） */
BigDecimal getUsedAmount(Long deptId, Long subjectId, Integer year, Integer month);
```

`BudgetCheckResult` 关键字段：`level(OK/WARN/BLOCK)`、`passed`、`budgetAmount/usedAmount/
checkAmount/totalAfterCheck/ratioPercent`、`controlWay/thresholdPercent`、`message`。

### check 裁决规则

1. 定位该年度 `RELEASED` 方案（多个取最新 id）；**无已下达方案 → 不控制放行**（结果注明）。
2. 取 部门×科目×月份 的当月预算；**无预算额度 → 不控制放行**（结果注明，提示补编）。
3. `占用率 = (已用 + 本次金额) / 当月预算 × 100%`（2 位小数）：
   - `占用率 < 阈值` → OK；
   - `占用率 ≥ 阈值` 且配置 WARN → 放行，level=WARN 带警告文案；
   - `占用率 ≥ 阈值` 且配置 BLOCK → **抛 BusinessException**（code=1000，消息含明细数），业务被阻止。
4. 同步调用示例：`POST /api/budget/check`
   `{"deptId":2,"subjectId":33,"year":2026,"month":9,"amount":5000}`。

## 4. REST 接口清单（前缀 /api）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /budget/plans | 方案分页（keyword/budgetYear/status） |
| POST | /budget/plans | 新增方案（初始草稿 + 默认配置 WARN/100） |
| GET/PUT/DELETE | /budget/plans/{id} | 详情 / 修改(仅草稿) / 删除(仅草稿) |
| POST | /budget/plans/{id}/release | 下达（草稿→已下达，锁定禁改） |
| POST | /budget/plans/{id}/close | 关闭（已下达→已关闭） |
| POST | /budget/plans/{id}/copy?targetYear= | 复制（默认下一年度草稿，含明细+配置） |
| GET | /budget/plans/{id}/matrix | 明细矩阵（部门×科目×12月 + 行/列合计） |
| PUT | /budget/plans/{id}/matrix | 保存矩阵（仅草稿，整体提交） |
| PUT | /budget/plans/{id}/control-config | 保存控制配置 |
| GET | /budget/options | 部门 + 末级费用/成本科目（编制下拉） |
| POST | /budget/check | 预算占用校验（手动/联调） |
| POST | /budget/usage | 登记执行数（付款接线/演示，冲销负数） |
| GET | /budget/analysis?planId&deptId | 部门×科目 预算/已用/余额/执行率 |
| GET | /budget/analysis/monthly?planId&deptId&subjectId | 月度执行曲线（Recharts） |

## 5. 付款校验接线约定（fin_payment 属 finance 模块）—— **已接线（2026-09-06）**

`fin_payment` 原只有 供应商/账户/金额/日期，**没有部门/科目列**，付款金额无法按约定表
直接归属到 部门×科目×期间 —— 采用「先校验、后推数」两段式接线，**本期已落地**
（V504 给 `fin_payment` 补 `dept_id`/`subject_id` 两列，均可空=兼容老数据）：

1. **校验挂点（已落地：FinPaymentServiceImpl.create，核销执行入口即创建）**：
   - 归属定位：表单显式 `deptId+subjectId` 优先；deptId 为空时按
     「供应商 → 最近采购单（pur_order，兜底最近应付单 fin_payable）创建人 →
     sys_user.dept_id」推导（`BudgetIntegrationMapper` 只读直查，pur_order 表缺失时降级跳过）；
     科目只认表单显式值；部门/科目任一定位不到 → **跳过校验**（老数据兼容）。
   - `budgetControlService.check(deptId, subjectId, year, month, amount)`：
     **BLOCK** 超预算抛 BusinessException，付款事务整体回滚（应付/账户均不动）；
     **WARN** 超预算放行，付款单 remark 追加「⚠超预算：占用率xx%」（200 列宽截断）
     并 `log.warn` 留痕。
2. **执行数推数（已落地：付款落库成功后，同一事务内）**：
   ```java
   BudgetUsageRecordForm form = new BudgetUsageRecordForm();
   form.setDeptId(deptId); form.setSubjectId(subjectId);
   form.setYear(year); form.setMonth(month);
   form.setAmount(paymentAmount);                 // 红冲/退款传负数
   form.setSourceType("PAYMENT");
   form.setSourceId(payment.getId());             // fin_payment.id
   form.setSourceNo(payment.getPaymentNo());      // 如 FK202609060001
   budgetControlService.record(form);
   ```
   同时把 dept_id/subject_id 落到 fin_payment 行上（PaymentForm/VO 透传可选字段）。
3. **依赖注入**：finance 模块 pom 增加 `erp-biz-budget` 依赖并注入
   `BudgetControlService`（依赖方向 finance → budget 单向，budget 仅依赖
   framework+basedata，无循环）。
4. `POST /api/budget/usage` 手动登记仍可用（演示/调整，冲销负数走 MANUAL）。

**接线实测（2026-09-06，共享 erp_db + 8080，全部通过）**：建方案（技术部×6401 主营业务
成本 2026-09 预算 1000，BLOCK）→ 付款 1200 挂该部门科目 → **创建被拒**（占用率 120%，
应付/账户零变动）；改 WARN → 创建成功，remark=「…｜⚠超预算：占用率120.00%」+ log.warn；
budget_usage_record 生成 PAYMENT 行（source_id=付款单 id、source_no=FK 单号）；
analysis 执行率=120.00%（used 1200 / budget 1000，余额 -200）；不带部门/科目的付款
（老数据形态）正常创建并跳过预算（日志「预算校验跳过」）。

**凭证口径局限说明**：fin_voucher 无部门列，借方发生额按【制单人所属部门】归属
（`sys_user.dept_id`），制单人无部门的凭证不计入。若后续 fin_voucher 增加 dept_id 列，
只需改 `CrossModuleMapper.sumVoucherDebit/sumVoucherUsedGrouped` 的 JOIN 条件，接口不变。

## 6. 数据表清单（V971 + V972）

- `budget_plan`：uk(plan_code)；idx(budget_year,status)。
- `budget_item`：uk(plan_id,dept_id,subject_id,month)；idx(dept_id,subject_id)。
- `budget_control_config`：idx(plan_id)，每方案至多一条在用行。
- `budget_usage_record`：idx(dept_id,subject_id,year,month)、idx(source_type,source_id)、idx(source_no)。
- **V972**：四表幂等补 `tenant_id BIGINT NOT NULL DEFAULT 1000` + `org_id BIGINT NOT NULL DEFAULT 1`
  （配合 V1011/V1012 多租户：BaseEntity 已映射两列、租户插件对未列入共享白名单的表自动追加
  `tenant_id=?`，预算表为租户/组织隔离业务数据，不在共享白名单）。

## 7. 前端页面（复用公共组件）

- `budget/plans`（PlanList + MatrixModal）：PageHeader/PageTable/StatusTag/useDebounce/useSubmitting；
  矩阵编制 Modal 为可编辑表格（部门/科目下拉 + 12 月 InputNumber 精度 2），行合计/列合计实时，
  保存整体提交；下达/关闭/删除均 Popconfirm；非草稿只读查看。
- `budget/analysis`（AnalysisPage）：方案/部门筛选 + 汇总卡 + 执行明细表
  （执行率 Progress，>100% exception 红色）+ Recharts 月度执行曲线（预算/已用双线）。

## 8. 已知边界与遗留

- DEPT_ONLY/SELF 级别的部门维度控制未做（跟随框架数据权限后续演进）。
- 复制方案编码自动追加 `-C`/`-C1` 后缀，不做跨年度编码规范强校验。
- 凭证部门归属为制单人部门的近似口径（见 §5 局限说明）。
