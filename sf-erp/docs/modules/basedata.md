# 基础资料模块（erp-basedata）

> 版本：v1.1（2026-09-06） · SQL 版本段：V10x + V13x（V101、V131） · 上下游：被 purchase/sales/inventory/mrp/finance 全量共享

## 1. 模块定位

基础资料是全部业务单据的主数据底座，对标金蝶 K3 / 用友 U8 的「基础资料」：物料、物料分类（树）、计量单位（换算组）、客户、供应商、仓库、库位、结算方式。

**依赖方向**：只依赖 framework（技术层）与 common；**禁止依赖任何业务模块**；上游模块只允许调用本模块暴露的 Service 接口，不允许直查本模块表。

## 2. 表清单（V101__basedata_init.sql）

| 表 | 说明 | 关键约束 |
|---|---|---|
| `base_unit` | 计量单位 | 换算组 group_code + 比率 rate DECIMAL(18,6)，组内换算 = 数量×源.rate/目标.rate |
| `base_material_category` | 物料分类（树） | parent_id=0 为根；uk_category_code |
| `base_material` | 物料 | uk_material_code；安全库存 DECIMAL(18,4)、参考成本 DECIMAL(18,2) |
| `base_customer` | 客户 | uk_customer_code；信用额度/期初应收 DECIMAL(18,2) |
| `base_supplier` | 供应商 | uk_supplier_code；期初应付 DECIMAL(18,2) |
| `base_warehouse` | 仓库 | uk_warehouse_code |
| `base_location` | 库位（仓库下多库位） | uk_location_wh_code(warehouse_id, code)；容量 DECIMAL(18,4) |
| `base_settle_type` | 结算方式 | uk_settle_code；内置现金/转账/承兑/月结 |

### 2.1 价格中心（V131__price_center.sql，版本段 V13x）

| 表 | 说明 | 关键约束 |
|---|---|---|
| `pur_price_record` | 采购价格流水（成交价库，**不含税** DECIMAL(18,4)） | uk_price_source(source_type, source_detail_id) 幂等；来源 ORDER 订单/INSTOCK 入库/RFQ 定标/CONTRACT 手工 |
| `base_price_contract` | 采购合同价 | 生效窗口 [from,to] 含两端；status=1 参与参照（参照时优先展示，标注"合同"） |
| `sal_price_level` | 销售等级价（物料×客户等级 A/B/C） | uk_price_level(material_id, level) |
| `sal_price_qty` | 销售批量价（物料×起订量阶梯） | uk_price_qty(material_id, min_qty)；命中"满足 qty 的最高 min_qty 档" |
| `sal_price_special` | 特价审批（客户×物料） | 状态 0待审/10通过/20驳回；wf_started 标记审批流通道；生效期空=不限 |
| `base_customer`（加列） | 客户等级 level VARCHAR(2) 默认 B | V131 幂等加列；等级价策略匹配维度 |

种子数据：结算方式 4 条、计量单位 2 组 5 条、分类 3 条（原材料/成品/半成品）、仓库 2 个。

## 3. REST 接口清单（统一前缀 `/api/basedata`，响应 Result<T>）

| 资源 | 接口 |
|---|---|
| 计量单位 | `GET /units`（分页 keyword）、`GET /units/enabled`、`GET/POST /units`、`PUT/DELETE /units/{id}` |
| 物料分类 | `GET /material-categories/tree`、`GET /{id}`、`POST`、`PUT /{id}`、`DELETE /{id}` |
| 物料 | `GET /materials`（分页：keyword/categoryId 含子孙/status）、`GET /materials/enabled?keyword`、`GET/POST /materials`、`PUT/DELETE /materials/{id}`、`PUT /materials/{id}/status?status=0|1`、`GET /materials/export`（CSV 流）、`GET /materials/import-template`、`POST /materials/import`（multipart file） |
| 客户 | `GET /customers`（分页 keyword=编码/名称/联系人）、`GET /customers/enabled`、`GET/POST`、`PUT/DELETE /customers/{id}` |
| 供应商 | `GET /suppliers` …（与客户对称） |
| 仓库 | `GET /warehouses`、`GET /warehouses/enabled`、`GET/POST`、`PUT/DELETE /warehouses/{id}` |
| 库位 | `GET /warehouses/{warehouseId}/locations`、`POST /warehouses/locations`、`PUT/DELETE /warehouses/locations/{id}` |
| 结算方式 | `GET /settle-types`（全量）、`GET /settle-types/enabled`、`GET/POST`、`PUT/DELETE /settle-types/{id}` |
| 采购价格流水 | `GET /basedata/prices/records`（分页 supplierId/materialId/sourceType，`basedata:price:list`）、`GET/POST /basedata/prices/records`、`DELETE /basedata/prices/records/{id}` |
| 录单参照（登录即可） | `GET /basedata/prices/last?supplierId&materialId`（合同价优先+最近成交价）、`GET /basedata/prices/history?materialId&supplierId&pageNum&pageSize`（日期倒序含来源单号）、`GET /basedata/prices/compare?materialId`（多供应商最低/最高/均价/最近价/合同价） |
| 合同价 | `GET/POST /basedata/price-contracts`、`PUT/DELETE /basedata/price-contracts/{id}`（`basedata:price:*`） |
| 等级价 | `GET/POST /basedata/price-levels`、`PUT/DELETE /basedata/price-levels/{id}` |
| 批量价 | `GET/POST /basedata/price-qtys`、`PUT/DELETE /basedata/price-qtys/{id}` |
| 特价审批 | `GET/POST /basedata/price-special`、`PUT /{id}/approve`、`PUT /{id}/reject`、`DELETE /{id}`（`sales:priceSpecial:*`，挂销售目录菜单 227） |
| 定价引擎 | `GET /basedata/pricing/quote?customerId&materialId&qty`（登录即可；销售订单/报价单录单参照） |

## 4. 对外 Service 能力（跨模块唯一入口）

| Service | 方法 | 供谁用 / 场景 |
|---|---|---|
| `BaseMaterialService` | `getByIds(Collection<Long>)` | 采购/销售/库存单据明细批量带出物料 |
| `BaseMaterialService` | `getEntityById(Long)` | 单据保存前校验物料存在性（抛 DATA_NOT_EXISTS） |
| `BaseMaterialService` | `listEnabled(String keyword)` | 单据行物料选择器下拉（最多 50 条） |
| `BaseCustomerService` | `getByIds` / `listEnabled` | 销售、财务应收：客户带出、信用额度校验（creditLimit）、期初应收（initReceivable） |
| `BaseSupplierService` | `getByIds` / `listEnabled` | 采购、财务应付：供应商带出、期初应付（initPayable） |
| `BaseWarehouseService` | `getByIds` / `listEnabled` | 库存、出入库、调拨：仓库选择与校验 |
| `BaseLocationService` | `listByWarehouse(Long)` | 库存模块按仓库带出库位（容量校验 capacity） |
| `BaseSettleTypeService` | `listEnabled` | 销售/采购/财务：结算方式下拉、按 days 计算账期到期日 |
| `BizCodeGenerator` | `nextCode(prefix)` | 单号生成（WL/KH/GYS/CK 前缀；单据模块 PO/SO 等前缀可复用此组件） |

**约定与保护规则**：
- 编码自动生成：`WL|KH|GYS|CK + yyyyMMdd + 3位流水`（Redis INCR，key 按天隔离、2 天过期）；表单传编码则以传入值为准（唯一校验）。
- 删除保护（抛 `ErrorCode.DATA_IN_USE`=1003）：分类（有子分类/被物料引用）、单位（被物料引用）、结算方式（被客商引用）、仓库（存在库位）。物料/客户/供应商/库位当前直接逻辑删除，**代码中已留 TODO**：单据与库存模块上线后补充引用校验。
- 批次/序列号管理开关（batchManaged/serialManaged）由库存出入库单读取，决定是否强制录入批次号/序列号。

## 5. 价格中心（V131）—— 采购价格库 + 销售价格策略

### 5.1 采购价格库（成交价自动沉淀）

- **扫描器**（`PriceCollectScheduleConfig`，30s/轮，开关 `price.auto-collect.enabled` 默认开）：
  零侵入扫「已审核未沉淀」单据写 `pur_price_record` —— 采购订单（status 10/20/30）、
  采购入库（status=10）、定标 RFQ（status=10 且明细有中标价）。只读采购模块约定表
  （`PriceSourceMapper` 手写 SQL），不 import 采购代码；`NOT EXISTS` 预筛 + uk 兜底天然幂等，
  逻辑删除的流水不会被重新沉淀。
- **价格口径**：一律**不含税**。订单/入库明细价是含税价，沉淀时按 `price/(1+taxRate/100)`
  换算（HALF_UP 4 位）；RFQ 定标价视为不含税裸价直接入库。
- **合同价优先**：录单参照 `GET /prices/last` 同时返回生效合同价（标注"合同"）与最近成交价；
  `compare` 对比行也带各供应商合同价。
- 手工补录（`POST /prices/records`，sourceType 固定 CONTRACT）供历史导入；流水不提供修改（不可变账本）。

### 5.2 定价引擎（PricingService.quote）

```
特价审批(通过+生效期内) ──命中──> 返回
      │ 未命中
等级价(物料×客户等级 A/B/C) ──命中──> 返回
      │ 未命中
批量价(满足 qty 的最高起订量档) ──命中──> 返回
      │ 未命中
ref_cost × sal.price.markup(默认1.3) ──> 返回（ref_cost 空 → NONE，无建议价）
```

- 返回 `{unitPrice(2位), hitStrategy, hitReason, references[]}`；references 为全部候选参照价
  （含未命中项），前端 Popover 并列展示；
- 客户等级取 `base_customer.level`（空/历史默认 B）；`sal.price.markup` 可配（application.yml）。

### 5.3 特价审批（双通道）

- 申请 `POST /basedata/price-special` 后自动尝试 `WorkflowService.startFlow("sal_price_special")`
  （V131 已加入 WfConstants 白名单与字典 wf_biz_type）：
  - **存在启用流程** → RUNNING，wf_started=1 生成待办；审批结果由 `findEffective`/分页
    对 `isApproved` **惰性同步**（0→10，audit_by=0 备注"审批流通过（自动同步）"）；
  - **无启用流程/未接白名单**（SKIPPED/异常）→ 管理员经 `PUT /{id}/approve|reject` 简单审批；
  - 流程条件全不满足 → AGREED 即时通过。
- 状态推进条件 UPDATE（WHERE status=0）防并发重复审批；已通过记录不可删（到期自动失效）。
- 通过且在生效期内的特价是定价引擎**最高优先级**。

### 5.4 录单参照（前端，最小侵入）

- 采购订单/销售订单/报价单新建 Modal 的物料行追加「价格参照」Popover
  （`pages/basedata/prices/PriceRefPopover.tsx`）：采购=合同价+最近价+历史3条；销售=命中策略+参照价列表。
  只追加展示元素与 Form.useWatch 联动，**不改提交逻辑**。
- 价格管理页 `pages/basedata/prices/PriceCenter.tsx`：Tab 采购价格流水（含供应商比价 Drawer）/ 合同价 /
  等级价 / 批量价 / 特价审批。
- 权限：`basedata:price:{list,add,edit,delete}`（菜单 207 挂基础资料 200）、
  `sales:priceSpecial:{list,add,audit,delete}`（菜单 227 挂销售 220）；参照接口（last/history/compare/quote）
  与下拉同口径——登录即可。

## 6. CSV 导入导出（物料）

- 模板：`GET /basedata/materials/import-template`（UTF-8 BOM + 表头 + 2 示例行）；列序：`物料编码,物料名称,规格型号,分类编码,单位名称,条码,批次管理,序列号管理,安全库存,参考成本价,状态`。
- 导入：逐行校验、错误跳过（部分成功），返回 `{total, success, fail, errors:[第N行: 原因]}`；编码列留空自动生成；分类编码/单位名称与系统不匹配的行报错。
- 导出：与分页同条件，上限 10000 行防呆。

## 7. 前端

- 路由片段：`src/router/modules/basedata.tsx` → `basedataRoutes`（materials/customers/suppliers/warehouses/prices，path 不带首斜杠，与 MainLayout 菜单一致）。
- 页面：`src/pages/basedata/materials/MaterialList.tsx`（左分类树右表格 + 分类/物料双 Modal + CSV 导入导出）、`customers/CustomerList.tsx`（含客户等级 A/B/C）、`suppliers/SupplierList.tsx`、`warehouses/WarehouseList.tsx`（仓库表 + 库位 Drawer 主子表）、`prices/PriceCenter.tsx`（价格中心五 Tab + `prices/PriceRefPopover.tsx` 录单参照件）。
- API/类型：`src/api/basedata.ts`（类型与后端 VO 一一对齐，含价格中心全部接口）。

## 8. 已知事项 / 预留

1. **单据引用删除校验**：物料/客户/供应商/库位删除的引用检查留有 TODO，待 purchase/sales/inventory 表结构稳定后在对应 Service.delete 中补充。
2. 敏感字段（参考成本价）按权限过滤返回——待 erp-system RBAC 的数据权限上线后接入。
3. 集成验证记录（2026-09-05）：本模块 28 项 curl 用例全部通过（含 WL/KH/GYS/CK 编码生成、DATA_IN_USE 删除保护、CSV 往返、库位仓库内唯一）。当时全量启动被两个**其他模块**问题阻塞：system 的 V15 脚本（sys_dept 缺 remark 列）导致 Flyway 中断、finance 与 inventory 的 `bizNoGenerator` Bean 名冲突；basedata 自身无阻塞项。
4. 价格中心验证记录（2026-09-06，V131）：V131 迁移成功（客户 level 幂等加列、5 张新表、菜单/权限种子）；
   扫描器自动沉淀 124+ 条流水（ORDER 57 / INSTOCK 65 / RFQ 2）；闭环实测：采购订单走 workflow 审批通过后
   40s 内沉淀（含税 999999 → 不含税 884954.8673 精确换算），last/history/compare 正确；
   定价引擎 A 客户等级价 11 > 批量价 10.5、B 客户命中批量价、C 客户走参考成本×1.3=13；
   特价 9.9 审批通过后 quote 命中 SPECIAL；审批流通道（FLOW-SP-PRICE-001）startFlow→待办→通过→惰性同步 0→10 全链路通过（联调后流程已停用，默认走管理员简单审批）。
   已知边界：客户等级修改沿用既有客户表单的手机号校验（演示数据座机会被拒，需一并改为合规手机号）；
   特价生效期重叠时取最新一条；`pur_price_record` 的 CONTRACT 来源由手工补录产生，合同价本体在 base_price_contract 维护。
