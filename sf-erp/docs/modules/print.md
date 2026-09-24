# 单据打印中心（erp-biz-report/print）设计文档

> 版本：v1.0（2026-09-05）· 范围：5 类单据模板的打印预览与浏览器打印（A4 版式，对标金蝶单据打印）
> 后端：`erp-backend/erp-biz-report/`（PrintController/PrintService/PrintMapper）；前端：`erp-web/src/pages/print/`、`src/api/print.ts`、`src/router/modules/print.tsx`
> SQL：**零新表纯聚合**（无 Flyway 脚本，全部按约定表结构只读 JOIN）

## 1. 模块定位

单据打印中心是报表中心内的**打印数据装配层**：为高频打印场景（销售订单/送货单/
采购订单/月度对账单/记账凭证）提供"一个聚合接口返回标准打印 VO"的只读服务，
前端用**一个统一 A4 版式组件（PrintPage）+ 5 个薄模板**渲染，`window.print()`
浏览器打印。全部口径与格式在后端收敛（金额千分位 2 位、数量去尾零、日期
yyyy-MM-dd、状态转中文、金额大写），前端零格式化逻辑，保证打印物全站一致。

跨模块约定与 ReportMapper 相同：不 import 业务模块代码，直接按各模块建表脚本
的约定列 JOIN；主子表按单据 id 直查（**草稿单也可打印**——纸质流转场景，
打印是单据自身的呈现而非统计口径）；仅对账单按已审核出库汇总（对账口径）。

## 2. 模板清单（bizType）

| bizType | 模板标题 | 数据源（主/子） | 版式要点 |
|---|---|---|---|
| sal_order | 销售订单 | sal_order / sal_order_detail + base_customer + base_warehouse | 标准单据：客户信息区、明细（物料/数量/单价/金额）、合计数量/金额 + 大写、签字栏 制单/审核/批准/客户确认 |
| delivery_note | 销售出库单（送货单） | sal_outstock / sal_outstock_detail + base_customer + base_warehouse | 送货单样式：客户/仓库/物料/数量/关联订单号、**客户签收**签字栏、一式三联说明脚注 |
| pur_order | 采购订单 | pur_order / pur_order_detail + base_supplier + base_warehouse | 供应商信息区、明细含税率%/交货日期、价税合计 + 大写、签字栏 制单/审核/批准/供方确认 |
| statement | 客户月度对账单 | 当月已审核 sal_outstock(_detail) + base_customer | id=**客户 ID** + `?month=yyyy-MM`；明细=当月出库单行；合计区=期初应收/本期应收发生/期末应收 + 大写（期末）；客户确认栏 |
| voucher | 记账凭证 | fin_voucher / fin_voucher_entry + fin_subject | 金蝶凭证样式：摘要/科目编码/科目名称/借方/贷方、合计行（借=贷）、**附单据 X 张**、签字栏 制单/审核/记账/出纳；不印大写金额 |

对账单口径：
- 明细 = 该客户当月（半开区间 `[月初, 次月初)`）已审核出库的明细行（与
  `/report/sales/detail` 同口径）；
- 期初应收 = `base_customer.init_receivable + 月初前全部已审核出库合计`
  （收款核销不回溯历史期间，与报表中心应收快照口径一致）；
- 期末应收 = 期初 + 本期应收发生。

金额大写：`com.erp.report.support.AmountInWords`（银行口径：组内连续零只读一个
"零"、组边界不足仟位补零、角分/整、负数"负"前缀）。示例：
`1234.56 → 壹仟贰佰叁拾肆元伍角陆分`、`100.00 → 壹佰元整`、`10500 → 壹万零伍佰`、
`105000 → 壹拾万伍仟`。

## 3. 接口

### 3.1 打印单据选择列表 `GET /report/print/list/{bizType}?pageNum&pageSize&keyword&month`

分页 `PageResult<PrintDocRowVO>`（统一收敛 编号/往来单位/日期/金额/状态 五列）：

| 字段 | 说明 |
|---|---|
| id | 单据 ID（statement 为客户 ID） |
| billNo / partyName / billDate / amount | 单据编号 / 往来单位（凭证为制单人）/ 日期（statement 为月份）/ 金额（statement 为当月应收发生额） |
| statusLabel | 状态中文（草稿/已审核/已过账…） |
| extra | statement=当月出库单数（"N 张出库单"），其他空 |

keyword 匹配单号/往来单位名（statement 为客户编码）；month 仅 statement 用（默认当月）。

### 3.2 打印数据 `GET /report/print/{bizType}/{id}?month=yyyy-MM`

返回标准打印 VO `PrintDataVO`（前端 PrintPage 直接渲染）：

```
bizType / company(苏州精工机械有限公司) / title / billNo / billDate
headerFields: [{label, value}]        主信息区（客户/供应商/仓库/状态…）
columns:      [{key,label,width,align}] 明细列定义（width=百分比，align=left|center|right）
details:      [{key: 展示字符串}]      明细行（值已格式化，空数据兜底空串/0.00）
totals:       [{label, value}]        合计区（合计数量/合计金额/期初期末/借贷合计…）
amountInWords                        合计金额大写（凭证模板不展示，数据仍返回）
attachmentNote                       凭证专有："附单据 X 张"
signatures:   [String]               签字栏（制单/审核人实名落款，其余留白）
remark / totalAmount                  表尾备注 / 数字口径合计
```

错误：bizType 非法 → `仅支持 sal_order/delivery_note/pur_order/statement/voucher`；
month 格式错 → `月份格式应为 yyyy-MM`；单据不存在 → `XX不存在或已删除`。

## 4. 前端结构

```
src/pages/print/
├── PrintCenter.tsx      # 打印中心：5 Tab（单据选择列表），点击进预览
├── DocListPane.tsx      # 每 Tab 列表（PageTable + 防抖搜索；statement 带月份选择）
├── PrintPreview.tsx     # A4 预览页（工具条 no-print + 纸张画布 + 入场渐显 0.25s）
├── PrintPage.tsx        # 统一 A4 版式：公司抬头+标题+单号行+主信息区+明细表+合计/大写+签字栏+页脚(打印人/打印时间)
├── templates.tsx        # 5 个模板（脚注/大写开关等版式差异）+ PRINT_TEMPLATES 注册表
└── printDoc.css         # 210×297mm 纸张样式 + @media print（只输出纸张、@page A4、分页控制）
```

打印实现（`window.print()`）：

- `@page { size: A4; margin: 10mm 12mm }`；
- `@media print` 用 **visibility 法**：`body * { visibility: hidden }` +
  `.print-sheet, .print-sheet * { visibility: visible }` + 纸张 `position:absolute`
  钉到页面左上角 —— 预览页嵌在 MainLayout（公共布局不可改），该组合可穿透
  侧边栏/顶栏/滚动容器输出干净纸张；
- 分页控制：`thead` 每页自动重复表头；明细行/签字栏/合计区 `page-break-inside: avoid`；
- 动效克制：仅预览入场渐显 0.25s（framer-motion），打印物本身无任何动效。

路由（`src/router/modules/print.tsx`，主线程合并进 `router/index.tsx`）：
`print`（打印中心）与 `print/preview/:bizType/:id`（预览，statement 带 `?month=`）。

## 5. 扩展新模板三步法

以新增"采购入库单（pur_instock）"为例：

1. **后端**（erp-biz-report）：
   - `PrintMapper.java` 加主子表查询（头 JOIN base_supplier/base_warehouse/sys_user×2，
     明细 JOIN base_material/base_unit，全部 `deleted=0`），`PrintMapper.xml` 写对应
     SELECT（列表 count+rows 两条 + 头/明细两条）；
   - `PrintServiceImpl` 加 `buildXxx(Long id)`：`base()` 骨架 + headerFields/columns
     （列宽百分比之和≈100）/details/totals/大写/签字栏（值全部服务端格式化）；
     `BIZ_TYPES` 加类型；`listDocs` switch 补两个分支。
2. **前端**（pages/print）：
   - `templates.tsx` 加 `XxxTemplate`（基于 `PrintPage`，差异用 `footnote`/
     `showWords` props）并注册进 `PRINT_TEMPLATES`；
   - `api/print.ts` 的 `BIZ_TYPE_META` 加一行（Tab 标签/列标题）+ `PrintBizType` 联合类型。
3. **验证**：`mvn -pl erp-biz-report -am compile` + `npx tsc --noEmit` + 打印中心
   新 Tab 选单 → 预览 → 浏览器打印预览核对 A4/大写/分页。

无需改路由（Tab 由 `BIZ_TYPE_META` 驱动自动出现）、无需建表、无需动公共组件。

## 6. 验证记录（2026-09-05）

- 后端编译：`mvn -pl erp-biz-report -am compile` / 全量 `mvn package -DskipTests` 通过
  （期间 erp-biz-finance、erp-framework 被并行任务中断编译，等待修复后重试通过）；
- AmountInWords：15 个用例独立编译运行 ALL PASS（1234.56→壹仟贰佰叁拾肆元伍角陆分、
  100→壹佰元整、10500→壹万零伍佰、105000→壹拾万伍仟、1234567890.12→壹拾贰亿…、负数）；
- curl（Bearer admin）：5 类打印数据接口各取一单全部 code=0 ——
  sal_order/9（合计 1,300.00/壹仟叁佰元整）、delivery_note/8（客户签收栏）、
  pur_order/1（价税合计 5,300.00/伍仟叁佰元整）、statement/4?month=2026-09
  （期初 0.00/本期 8,500.00/期末 8,500.00/捌仟伍佰元整）、
  voucher/18（借贷各 1,234.56 平衡，附单据 3 张）；
  5 类列表接口分页/keyword/月份过滤正常；错误兜底（非法 bizType、2026-13、
  不存在单据）均返回 code=1000 中文错误；草稿单（sal_order/8）可打印；
- 前端：`npx tsc --noEmit` 0 错误、`npx vite build` 通过；
- 渲染验证（浏览器运行时在本环境不可用，采用等效 SSR 验证）：vite.ssrLoadModule
  加载真实组件 + react-dom/server renderToString，数据走真实后端接口链路 ——
  PrintCenter（5 Tab + 真实分页列表 + 预览按钮）与 5 个预览页（公司抬头/单据标题/
  单号/大写金额/首行明细/签字栏/打印时间页脚/no-print 工具条，凭证含附单据张数、
  不印大写）共 6 组 52 项断言 ALL PASS；
- 验证数据：库内 sal/pur 单据为并行任务已有数据；fin_voucher 当时被清空，
  补插 1 张已过账测试凭证 PZ202609050099（借 1002 银行存款 1234.56 /
  贷 6001 主营业务收入 1234.56）。
