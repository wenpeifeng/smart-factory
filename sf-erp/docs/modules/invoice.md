# 发票管理模块（erp-invoice）设计文档

> 版本：v1.0（2026-09-05） · 对标金蝶发票管理（增值税电子发票场景，简化版）
> 范围：销项发票（开票申请/开票/红冲/待开票清单）、进项发票（登记/认证/红冲）、
> 税务统计（期间税负/月度价税走势）、客户/供应商对账辅助。
> 后端：`erp-backend/erp-invoice`；前端：`erp-web/src/pages/invoice/`、`src/api/invoice.ts`、
> `src/router/modules/invoice.tsx`；SQL：`script/sql/migration/V941__invoice_init.sql`（V94x 段）。

## 1. 模块定位与单据流

```
销售出库单(sal_outstock, 已审核) ──开票申请──→ 销项发票(待开票) ──开票──→ 已开票 ──红冲──→ 已红冲 + 负数红字票
                                                    │                          （净额 0，出库单回到待开票清单）
采购入库单(pur_instock, 已审核) ──登记──→ 进项发票(待认证) ──认证──→ 已认证 ──红冲──→ 已红冲 + 负数红字票
                                                    │                （红字票直接已认证，认证日期=红冲当日）
                                                    └→ 已认证税额进入税负统计
统计：销项(按开票日期) / 进项(按认证日期) / 应纳增值税=销项−进项 / 税负率=应纳÷不含税销售额
```

## 2. 数据表（V941，2 张表）

| 表 | 说明 | 关键约束 |
|---|---|---|
| inv_sales_invoice | 销项发票 | uk_sinv_no(invoice_no)；customer_id/biz_id/status/invoice_date/source_invoice_id 索引 |
| inv_purchase_invoice | 进项发票 | uk_pinv_no(invoice_no)；supplier_id/biz_id/status/invoice_date/auth_date/source_invoice_id 索引 |

两表结构对称，关键列：

| 列 | 说明 |
|---|---|
| invoice_no | 发票号码（8 位数字；销项系统取号 10000001 起 Redis INCR，降级随机号段；进项票面号优先） |
| invoice_date | 开票日期（销项在开票/红冲时写入；进项登记时写入，默认取入库日期） |
| biz_type / biz_id / biz_no | 业务单据关联（SAL_OUTSTOCK→sal_outstock / PUR_INSTOCK→pur_instock） |
| amount / tax_rate / tax_amount / total_amount | 金额(不含税) / 税率%(13/9/6) / 税额=金额×税率% HALF_UP 2位 / 价税合计=金额+税额 |
| red_flag | 红字标志：0 蓝字 / 1 红字（负数发票） |
| source_invoice_id / source_invoice_no | 红字票关联的原蓝字发票 |
| status | 销项：0待开票 10已开票 20已红冲；进项：0待认证 10已认证 20已红冲 |
| issue_by / issue_time（销项）| 开票人 / 开票时间 |
| auth_date / auth_by（进项）| 认证日期 / 认证人（进项税额归属期间依据） |

## 3. 状态机与并发控制

- 销项：`0 待开票 → 10 已开票 → 20 已红冲`（终态）；进项：`0 待认证 → 10 已认证 → 20 已红冲`。
- 流转一律**条件 UPDATE**（WHERE 携带旧状态，XML 见 InvSalesInvoiceMapper / InvPurchaseInvoiceMapper），
  影响行数 0 即并发冲突，抛业务异常提示刷新重试；
- 红冲在**同一事务**内完成：原票 10→20 + INSERT 负数红字票（金额/税额/价税合计取负，
  红字票自身 status=10，销项写开票人、进项写认证日期=红冲当日）；
- 防重复开票：同一出库/入库单存在 `red_flag=0 AND status IN (0,10)` 的蓝字发票即拒绝；
  已红冲(20)视为净额 0、**允许重新开票**（出库单重新回到待开票清单）；
- 金额不变量：`tax_amount = amount × tax_rate ÷ 100`（HALF_UP 2 位）、`total_amount = amount + tax_amount`，
  全链 BigDecimal。

## 4. 统计与对账口径（重要）

| 指标 | 口径 |
|---|---|
| 销项税额/销售额 | `status IN (10, 20)`（待开票不计；**已红冲蓝票仍计入**，由红字负数票自然冲减，净额正确），按 invoice_date 归属期间 |
| 进项税额 | 同上口径，按 **auth_date**（认证所属期）归属；红字票认证日期=红冲当日 |
| 应纳增值税 | 销项税额 − 进项税额（负数表示留抵） |
| 税负率 | 应纳增值税 ÷ 不含税销售额 × 100（HALF_UP 2 位，销售额为 0 时返回 0） |
| 月度价税走势 | 全年 12 个月补齐，销项/进项均按 invoice_date 分月 SUM(total_amount)/SUM(tax_amount) |
| 对账辅助 | 业务侧=期间内已审核出库/入库单金额合计（不含税）；开票侧=期间内已生效发票净额；差额=业务−开票（正数即未开票） |

## 5. 跨模块协作约定（只读，不 import 对方代码）

| 表 | 归属 | 读取时机 | 写入 |
|---|---|---|---|
| sal_outstock / sal_outstock_detail | sales V30x | 开票申请：校验已审核、明细汇总不含税金额（明细缺失回退单头 total_amount）；待开票清单 | 不写 |
| pur_instock / pur_instock_detail | purchase V20x | 登记：校验已审核、默认税率取明细唯一非 0 税率否则 13%；待登记清单 | 不写 |
| base_customer / base_supplier | basedata V10x | 名称回显（InvoicePartnerNameLookup，表未落库自动降级名称 null，前端展示"客户 #id"） | 不写 |

- 类名 `InvoicePartnerNameLookup` / `InvoiceNoGenerator`：避免与 finance 模块同名 Bean 冲突
  （PartnerNameLookup 冲突会导致 Spring 启动失败，模块并发交付时的强制约定）。

## 6. 接口清单（统一前缀 /api，JWT 登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /invoice/sales | 销项分页：customerId/status/keyword/from/to |
| GET | /invoice/sales/{id} | 销项明细 |
| POST | /invoice/sales/from-order/{outstockId} | 开票申请（可空体；taxRate 默认 13%，仅允许 13/9/6） |
| POST | /invoice/sales/{id}/issue | 开票（0→10，写开票日期/开票人） |
| POST | /invoice/sales/{id}/reverse | 红冲（10→20 + 负数红字票关联原票号） |
| GET | /invoice/sales/toBeInvoiced?customerId | 客户已出库未开票清单（可选客户过滤） |
| GET | /invoice/purchases | 进项分页：supplierId/status/keyword/from/to |
| GET | /invoice/purchases/{id} | 进项明细 |
| POST | /invoice/purchases/from-instock/{instockId} | 登记（可空体；invoiceNo 8位可选/invoiceDate/taxRate） |
| POST | /invoice/purchases/{id}/authenticate | 认证（0→10，写认证日期/认证人） |
| POST | /invoice/purchases/{id}/reverse | 红冲（10→20 + 负数红字票冲减已认证税额） |
| GET | /invoice/purchases/toBeRegistered?supplierId | 供应商待登记清单 |
| GET | /invoice/statistics/tax?from&to | 期间税负（缺省近一年） |
| GET | /invoice/statistics/monthly?year | 年度 12 个月价税走势（缺省当前年） |
| GET | /invoice/reconciliation?partnerType&partnerId&from&to | 对账辅助（customer/supplier） |

## 7. 前端

- `pages/invoice/sales/SalesInvoiceList.tsx`：本年税负指标卡（销售额/销项/进项/应纳/税负率，
  开票红冲联动刷新）+ 销项列表（客户/状态/单号筛选，红字票负数标红）+ 从出库开票弹窗
  （选客户 → 待开票清单单选 → 税率 13/9/6）+ 开票/红冲操作；
- `pages/invoice/purchases/PurchaseInvoiceList.tsx`：进项列表（供应商/状态/单号筛选）
  + 从入库登记弹窗（待登记清单单选 + 票面发票号/开票日期/税率可选）+ 认证/红冲操作；
- `src/api/invoice.ts`：类型与请求定义（VO 与后端对齐）；`src/router/modules/invoice.tsx`：
  路由片段 `invoiceRoutes`（invoice/sales、invoice/purchases，与 MainLayout 菜单一致，
  由主线程合并进 router/index.tsx）。

## 8. 自测记录（2026-09-05，本地 8081 实例）

| 用例 | 结果 |
|---|---|
| 出库单(明细 600+400=1000) 开票申请默认 13% | tax=130.00，total=1130.00，状态待开票 |
| 待开票清单：申请前包含/申请后扣除/红冲后重新出现 | 通过 |
| 同一出库单重复开票 | 拒绝（1001 出库单已存在未红冲的发票） |
| 非法税率 17% | 拒绝（税率仅允许 13/9/6） |
| 开票 | 0→10，invoice_date=当日，issue_by=当前用户 |
| 销项红冲 | 原票→20 已红冲；新票 -1000/-130/-1130，redFlag=1，关联原票号 |
| 进项登记（入库 5000，明细 13%） | 默认税率取明细 13%，tax=650，total=5650，待认证 |
| 认证 | 0→10，auth_date=当日 |
| 税负 | 认证前 销项130/进项0/应纳130/税负率13%；认证后 进项650/应纳-520；销项红冲后 销项净0/应纳-650 |
| 月度走势 | 9 月：销项净 0（红冲冲减）、进项 5650/650 |
| 对账 | 客户：业务1000 vs 开票净0（红冲后）差1000；供应商：业务5300 vs 开票5000 差300 |
