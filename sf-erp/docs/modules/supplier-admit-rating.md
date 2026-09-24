# 供应商准入与交易评级（V141）

> 模块归属：erp-biz-basedata（评级表前缀 `pur_`，与 V131 `pur_price_record` 同理——按"约定表直查"模式读采购/QC 表，不 import 对方 Java 代码）。
> SQL 迁移：`script/sql/migration/V141__supplier_admit_rating.sql`（basedata 段 V14x）。
> 前端页面：`src/pages/basedata/suppliers/SupplierAdmit.tsx`（准入）、`src/pages/purchase/suppliers/SupplierScoreReport.tsx`（评级）。

## 1. 供应商准入流程

```
登记（草稿0，同时自动创建停用 base_supplier，GYS 编码自动生成）
   │ submit（校验营业执照附件必填）
   ▼
待审（10）──自动 tryStartFlow(bizType=supplier_admit)
   │   ├─ workflow 存在启用流程 → RUNNING，wf_started=1，生成待办（分页查询惰性同步结果 10→20）
   │   ├─ 流程条件全不满足 → AGREED，即时通过（20，audit_by=0）
   │   └─ 无流程/未接白名单（SKIPPED）→ 管理员经 approve/reject 简单审批
   ├─ 通过（20）：base_supplier.status=1 启用，可参与采购
   ├─ 驳回（30）：保持停用，可编辑后重新 submit
   └─ 黑名单（40）：base_supplier.status=0 + is_blacklist=1
```

- 双通道审批复用 V131 特价审批先例（`SalPriceSpecialServiceImpl`）：`workflowService.startFlow` + 惰性 `isApproved` 同步 + 条件 UPDATE（`WHERE status=10`）防并发重复审批。
- 黑名单只在"已通过（20）"的申请上操作；移出黑名单恢复 20 并重新启用。
- 删除仅限草稿/驳回（同时逻辑删登记时创建的 base_supplier）。

### 黑名单下单拦截（purchase 模块，最小侵入）

- `purchase/BasedataMapper` 新增只读方法 `selectSupplierBlacklist(id)`（查 `base_supplier.is_blacklist`）。
- `PurOrderServiceImpl`：
  - `validateBaseInfo`（订单创建/修改入口）追加 `checkSupplierNotBlacklisted`；
  - `audit`（三段式审核入口，供应商必填校验后）追加同一调用。
  - 拦截抛 `BusinessException.ofKey("erp.biz.sup-blacklisted", supplierId)`（i18n 已配 zh/en）。

## 2. 交易评分

### 2.1 数据来源（SupplierScoreSourceMapper，只读注解 SQL）

| 指标 | 来源表 | 口径 |
| --- | --- | --- |
| 交易额/订单数 | `pur_order`（status∈10/20/30，按 order_date 入期） | SUM(total_amount) / COUNT |
| 准时交货率 | `pur_instock`(status=10) LEFT JOIN 订单明细最早交期 | 入库日期 ≤ 订单明细 MIN(delivery_date) 记准时；无交期单不参与分母 |
| 来料合格率 | `qc_inspect`（status=10，source_type='pur_instock'） | 合格数 / (合格数+不合格数) |
| 退货率 | `pur_return`(status=10) 明细数量 / 同期 `pur_instock_detail` 数量 | 同期近似口径，封顶 100% |

### 2.2 评分算法（PurSupplierScoreServiceImpl，常量权重）

```
ontimeRate    = dueCount==0 ? 100% : ontimeCount/dueCount×100      # 无带交期入库单记 100%（不拖累）
qualifiedRate = 未检 ? 100% : qualifiedQty/(qualified+unqualified)×100  # 未送检不扣分
returnRate    = instockQty==0 ? 0% : min(returnQty/instockQty×100, 100) # 无入库无法定义记 0

totalScore = 100 × ( 0.30×ontimeRate + 0.40×qualifiedRate + 0.30×(1−returnRate) )
            （百分数值代入，HALF_UP 2 位；权重 W_ONTIME/W_QUALIFIED/W_RETURN 常量，和为 1）

grade: A ≥ 90，B ≥ 75，C ≥ 60，D < 60
```

权重当前为代码常量（0.30/0.40/0.30）；如需运营可调，后续可平滑替换为 `sys_dict_data`（dict_type=`sup_score_weight`）读取，接口不变。

### 2.3 周期与重算

- 周期类型 `MONTH`（yyyy-MM）/ `QUARTER`（yyyy-Qn）。
- 定时器 `SupplierScoreScheduleConfig`：每月 1 日 01:40 计算上一个自然月；若上月为季末月（3/6/9/12）同时计算上一季度。开关 `supplier.score.enabled`（默认开）。单轮异常吞掉（下月重算或手工补算），`@EnableScheduling` 复用本模块 V131 配置类不重复标注。
- 手工重算：`POST /purchase/supplier-scores/recalc?periodType=&period=`（period 空=最近一个已完结周期）。
- 重算 = 事务内"先删该 (periodType, period) 全部行再插入"，幂等；uk(supplier_id,period_type,period) 兜底防并发重号。

## 3. 表结构

- `base_supplier_admit`：准入申请主表（资质附件 license_file/cert_files、审批与拉黑审计字段、wf_started）。
- `base_supplier.is_blacklist`：V141 幂等加列（0/1），采购拦截依据。
- `pur_supplier_score`：评分结果（指标列 + total_score + grade + uk 周期唯一）。

## 4. 接口清单

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | /basedata/supplier-admit | basedata:supplierAdmit:list | 分页（惰性同步审批结果） |
| GET | /basedata/supplier-admit/{id} | basedata:supplierAdmit:list | 详情 |
| POST | /basedata/supplier-admit | basedata:supplierAdmit:add | 登记（submit=true 立即提交） |
| PUT | /basedata/supplier-admit/{id} | basedata:supplierAdmit:edit | 编辑（草稿/驳回） |
| PUT | /basedata/supplier-admit/{id}/submit | basedata:supplierAdmit:edit | 提交申请 |
| PUT | /basedata/supplier-admit/{id}/approve | basedata:supplierAdmit:audit | 通过（启用供应商） |
| PUT | /basedata/supplier-admit/{id}/reject | basedata:supplierAdmit:audit | 驳回 |
| PUT | /basedata/supplier-admit/{id}/blacklist | basedata:supplierAdmit:blacklist | 拉黑 |
| PUT | /basedata/supplier-admit/{id}/unblacklist | basedata:supplierAdmit:blacklist | 移出黑名单 |
| DELETE | /basedata/supplier-admit/{id} | basedata:supplierAdmit:delete | 删除（草稿/驳回） |
| GET | /purchase/supplier-scores | purchase:supplierScore:list | 评分分页 |
| GET | /purchase/supplier-scores/summary | purchase:supplierScore:list | 汇总报表（等级分布/均分/交易额） |
| POST | /purchase/supplier-scores/recalc | purchase:supplierScore:recalc | 手工重算 |

## 5. 菜单/权限种子（V141 内置）

- 208 供应商准入（挂基础资料目录 200）+ 按钮 2081~2085（add/edit/audit/delete/blacklist）。
- 218 供应商评级（挂采购管理目录 210）+ 按钮 2181（recalc）。
- 字典 `wf_biz_type` 补 `supplier_admit`（sort=9）；workflow 白名单 `WfConstants.BIZ_TYPES` 同步加一行（与 V131 加 sal_price_special 同一做法）。

## 6. i18n key（messages_zh_CN / messages_en）

`erp.biz.sup-admit-not-editable` / `sup-admit-not-submittable` / `sup-admit-license-required` /
`sup-admit-not-pending` / `sup-admit-blacklist-approved-only` / `sup-admit-not-blacklisted` /
`sup-admit-delete-draft-only` / `sup-admit-not-exists` / `sup-blacklisted` /
`sup-score-period-type-invalid` / `sup-score-period-invalid`

## 7. 遗留事项

- 资质附件当前为文件标识/URL 文本录入，待统一文件上传组件接入后替换为上传控件。
- 评分权重为常量；若需运营可调，加 `sup_score_weight` 字典并在服务内读取。
- 菜单 i18n（MainLayout 菜单名 key）由菜单协调者统一挂载：`basedata/supplier-admit`（基础资料组）与 `purchase/supplier-score`（采购管理组）。
