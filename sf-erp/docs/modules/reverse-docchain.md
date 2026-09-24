# T7：统一单据状态机 + 通用红冲 + 单据联查

> 模块归属：实现收口在 `erp-biz-report`（其 Maven 依赖已覆盖 purchase/sales/inventory/finance/cost，
> 天然是跨模块编排层）；`DocStatus` 枚举放 `erp-common`；SQL 为 `V541__finance_reverse_doc_chain.sql`。

## 1. 通用红冲（ReverseService）

### 1.1 语义

任意"已审核"（status=10）单据可发起红冲，一个事务内完成：

1. 生成负数反向单（数量/金额取负，单号 = **原单号 + R**，引用原单的业务关联字段），
   status 直接落 10（红冲单生成即完成过账，不走草稿）；
2. 反向过账：库存（按原单 `inv_movement` 流水腿逐腿精确反向，同批次同库位）、
   成本（与对应退货流同口径）、应收应付（插入相反符号 AR/AP 行）、
   收付款（核销回冲 + 账户余额反向变动 + 账户流水）；
3. 数量回退：订单累计入库/出库数、入库/出库明细已退数（全部条件 UPDATE 守卫，不足回滚）；
4. 原单 status 10→20（REVERSED）+ `biz_reverse_log` 配对留痕（uk_reverse_source 唯一键防重复红冲）。

### 1.2 支持类型与反向过账明细

| docType | 表 | 库存反向 | 成本 | AR/AP | 数量回退 |
|---|---|---|---|---|---|
| PUR_INSTOCK | pur_instock | 流水腿 PUR_IN 出库（PUR_RET 类型落新流水） | onStockOut(PUR_RET) | 负应付 | 订单 received_qty -，刷新订单进度 |
| SAL_OUTSTOCK | sal_outstock | 流水腿 SAL_OUT 入库（SAL_RET） | onStockIn(SAL_RET) | 负应收（红字，与 sal_return 同口径） | 订单行 out_qty - |
| PUR_RETURN | pur_return | 流水腿 PUR_RET 入库（PUR_IN） | onStockIn(PUR_RET) | —（采购退货本就不生成负应付） | 入库明细 returned_qty -，订单 received_qty + |
| SAL_RETURN | sal_return | 流水腿 SAL_RET 出库（SAL_OUT） | onStockOut(SAL_RET) | 正应收对冲（退货原生成负应收） | 出库明细 returned_qty -，订单 out_qty + |
| RECEIPT | fin_receipt | — | — | 应收 settled_amount 回冲（守卫） | 账户余额扣减 + EXPENSE 流水 |
| PAYMENT | fin_payment | — | — | 应付 settled_amount 回冲（守卫） | 账户余额回加 + INCOME 流水 |

边界规则：

- 原单蓝字 AR/AP 已被核销时，红冲单据只插反向行对冲余额，**不自动撤销收付款**——
  报错提示"先红冲对应收付款单"（`erp.biz.reverse.settle-conflict`）。
- 库存已被消耗（同批次/库位余量不足）时流水腿反向失败，整体回滚——先处理消耗或退货。
- 红冲单自身不允许再次红冲（biz_reverse_log reverse 侧存在记录即拒绝）。
- 收付款单无 status 列，"已红冲"以 biz_reverse_log 为准（联查节点 reversed 标记同源）。

### 1.3 REST 接口（DocOpsController，权限码 = sys_menu V541 按钮）

| 方法 | 路径 | 权限码 |
|---|---|---|
| POST | /api/purchase/instock/{id}/reverse | purchase:instock:reverse |
| POST | /api/purchase/return/{id}/reverse | purchase:return:reverse |
| POST | /api/sales/outstock/{id}/reverse | sales:outstock:reverse |
| POST | /api/sales/return/{id}/reverse | sales:return:reverse |
| POST | /api/finance/receipt/{id}/reverse | finance:receipt:reverse |
| POST | /api/finance/payment/{id}/reverse | finance:payment:reverse |
| GET | /api/basedata/doc-chain?bizType=&bizId= | basedata:docChain:list |

请求体（reverse）：`{"reason": "红冲原因"}`；响应：`{docType, reverseDocId, reverseDocNo, reverseAmount}`。

## 2. 单据联查（DocChainService）

只读接口 `GET /api/basedata/doc-chain?bizType=&bizId=`，返回锚点 + 上游链 + 下游链（BFS，深度上限 8）。
邻接查询全部下推 `DocChainMapper.xml`（跨模块只读 JOIN），Service 只做编排与节点补齐
（docType/docName/reversed）。

邻接关系（→ 下游）：

```
销售：sal_quotation → sal_order → sal_outstock → { sal_return, inv_sales_invoice(biz_type=SAL_OUTSTOCK,biz_id),
       fin_receivable(biz_no=out_no) } → fin_receipt(经 fin_receipt_item) → fin_voucher(source_type/source_id)
退货：sal_return → fin_receivable(biz_no=return_no 红字)；sal_return/pur_return → fin_voucher(SAL_RET/PUR_RET)
采购：pur_order → pur_instock → { pur_return, inv_purchase_invoice(biz_type=PUR_INSTOCK,biz_id),
       fin_payable(biz_no=instock_no) } → fin_payment(经 fin_payment_item) → fin_voucher(PAYMENT)
红冲：任意单据 ↔ biz_reverse_log ↔ 红冲单（双向）
```

节点 VO：`{docType, docId, docNo, docName, bizDate, amount, status, reversed}`（status 为各表原值字符串化）。

前端：`src/pages/basedata/DocChain.tsx`（路由 `basedata/doc-chain`，菜单位 基础资料→单据联查），
链上可红冲节点直接提供红冲按钮（按权限码显隐）。

## 3. 统一状态机（DocStatus 枚举 + 存量盘点）

枚举：`com.erp.common.enums.DocStatus`（erp-common）——
`DRAFT(0) / SUBMITTED(5) / AUDITED(10) / REVERSED(20) / CLOSED(90)`。
**不迁移存量数据**，仅作为统一语义定义；红冲向出入库/退货单写入的新值只有 20(REVERSED)。

存量 status 取值盘点（2026-09 核对）：

| 单据表 | 取值 | 语义 |
|---|---|---|
| pur_instock / pur_return | 0,10,(20新增) | 草稿 / 已审核 / 已红冲 |
| sal_outstock / sal_return | 0,10,(20新增) | 草稿 / 已审核 / 已红冲 |
| pur_order | 0,10,20,30,90 | 草稿/已审核/部分入库/已完成/已关闭 |
| sal_order | 0,10,20,30,90 | 草稿/已审核/部分出库/已完成/已关闭（另有 wf_started 工作流标记） |
| sal_quotation | 0,10,20,30,40 | 草稿/已报价/已转订单/已过期(动态判定)/已作废 |
| sal_delivery_notice | 0,10,20,30 | 草稿/已通知/已出库/已取消 |
| inv_other_in/out, inv_check | 0,10,20 | 草稿/已审核/已完成（inventory 模块口径） |
| fin_receivable / fin_payable | UNSETTLED/PARTIAL/SETTLED | 字符串核销状态（金额维度） |
| fin_voucher | DRAFT/POSTED 等 | 字符串凭证状态 |
| inv_sales_invoice / inv_purchase_invoice | 0,10,...（发票模块口径） + red_flag 红字标记 | |
| fin_receipt / fin_payment | （无 status 列） | 保存即生效，红冲态看 biz_reverse_log |

映射约定（DocStatus 注释同步）：0→DRAFT，10→AUDITED，订单类 20→部分执行（出入库单 20→REVERSED），
30/90→CLOSED。

## 4. SQL（V541，finance 段）

- `biz_reverse_log` 红冲台账（uk_reverse_source 唯一防重）；
- sys_menu 种子：4801 单据联查页面 + 4811~4816 六个红冲按钮（INSERT IGNORE，未写 org_id/tenant_id）。

已在临时库 erp_db_t7 全量执行全部迁移脚本验证（含 V541），重跑 V541 幂等通过。

## 5. 遗留 / 主线程接线项

1. 各单据列表页（采购/销售/财务模块页面）可按需追加"红冲"按钮——
   API 已就绪（见 1.3），前端封装 `reverseDoc(docType, id, reason)`（src/api/basedata.ts）；
   权限码见上表，按钮用 `<HasPerm>` 包裹。
2. 列表页状态渲染建议补充 20=已红冲（红字 Tag），目前未知值会显示原始数字。
3. 联查页面菜单（4801）路由 `basedata/doc-chain` 已加入 basedata 路由片段，需主线程确认合并。
4. 成本红冲采用"当前移动加权价"口径（与退货流一致），与原单实际成本可能存在小额差异，
   差额留在成本差异报告中反映（后续可增强为按原 cost_flow 精确回冲）。
