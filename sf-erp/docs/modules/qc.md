# 质量管理模块（erp-qc）设计说明

> 模块：erp-backend/erp-qc + erp-web/src/pages/qc
> SQL：script/sql/migration/V701__qc_init.sql（版本段 V70x）
> 状态：开发完成，后端全链路（模板→来源单生成检验单→实检值录入→结论→不合格处置→统计）curl 实测通过

## 1. 功能范围（对标金蝶质量管理）

| 功能 | 表 | 说明 |
|---|---|---|
| 检验模板 | qc_template / qc_template_item | 模板=检验类型+适用物料分类+检验项目子表；检验类型 IQC/IPQC/FQC/OQC |
| 检验单 | qc_inspect / qc_inspect_item | 手工/按来源单生成；明细=模板快照+实检值；状态机 草稿→已检验 |
| 不合格品处置 | qc_reject | 从已检验且不合格数>0 的检验单生成；处置决定 退货/返工/让步接收/报废 |
| 质量统计 | （只读统计） | 按物料/供应商合格率、检验单量趋势 |

## 2. 状态机与口径（只允许正向流转）

- **检验单**：`0草稿 → 10已检验`（条件 UPDATE 防并发重复提交，`QcInspectMapper#submit`）；已检验为终态，禁改禁删。
- **数量口径**：合格数 + 不合格数 = 抽检数；结论=1(合格) ⇔ 不合格数=0，结论=2(让步接收)/3(退货) 时不合格数必须>0。
- **抽检数默认值**：来源数量 × 模板项目最大抽检比例%（向上取整，至少 1），生成后可改。
- **处置数量守卫**：同一检验单累计处置数量 ≤ 该单不合格数（`QcRejectMapper#sumDisposedQty`，修改时排除自身）。

## 3. 单号规则

`前缀 + yyyyMMdd + 4位流水`，Redis INCR（key=`erp:no:{前缀}:{日期}`，48h 过期，`QcNoGenerator`）：

| 单据 | 前缀 | 示例 |
|---|---|---|
| 检验单 | QC | QC202609050001 |
| 不合格品处置单 | RJ | RJ202609050001 |

## 4. 检验类型与来源单映射

| 来源单据（source_type） | 检验类型 | 供应商 | 说明 |
|---|---|---|---|
| pur_instock（V201） | IQC | 取入库单 supplier_id | 按入库明细物料行逐行生成检验单 |
| mrp_work_order（V601） | FQC | 空 | 工单单物料，一单一检验单 |
| sal_return（V303） | IQC（复检口径） | 空 | 按退货明细物料行生成；系统检验类型仅 4 种，退货复检归 IQC |
| （空）手工创建 | 任意 | 可选 | 明细按模板自动带出或手工录入 |

**模板匹配规则**（`QcInspectServiceImpl#matchTemplate`）：检验类型一致 + 启用状态；
物料分类精确匹配优先，其次通用模板（category_id 为空）；都无匹配则生成空明细检验单（允许手工补）。

**快照设计**：检验单明细在生成时复制模板项目行（项目名/标准值/上下限/检验方法/抽检比例），
模板后续修改/删除不追溯影响历史检验单。

## 5. 与一期单据的集成约定（挂点接口，主线程接线）

本模块【不创建、不修改】他人模块的表；`CrossModuleMapper` 只读查询以下约定表：
pur_instock / pur_instock_detail、mrp_work_order、sal_return / sal_return_detail、
base_material（单位经 unit_id 关联 base_unit.name）、base_supplier、base_material_category。

### 5.1 自动生成检验单（已可独立调用）

`POST /api/qc/inspects/from/{bizType}/{bizId}`，bizType ∈ pur_instock / mrp_work_order / sal_return：

- 读来源单 + 明细物料行 → 每个物料行生成一张草稿检验单（单号 QC 流水，source_no 快照来源单号）；
- **幂等**：同一来源单+物料已存在未删除检验单（含草稿/已检验）则跳过该物料（返回数组对应位为 null）；
- 返回新生成的检验单 ID 列表。

### 5.2 审核卡点（重要，一期模块审核前应调用）

`GET /api/qc/inspects/pending?bizType={bizType}&bizId={bizId}`：

- `pending=true`：该来源单仍有**草稿（未检验）**检验单 → 按约定应**阻止来源单审核**；
- `pending=false`：无草稿（从未生成或已全部提交检验）→ 允许审核；
- 推荐接线点：采购入库单审核（`PUT /purchase/instock/{id}/audit`）前置校验（先调 from 生成、检验完成后再审核入库）；
  生产工单完工、销售退货入库同理；
- 约定（当前版本）：qc 模块**不反向拦截**一期单据审核（避免跨模块强耦合），由一期模块审核逻辑主动调用本接口实现卡点；
  后续若要求强一致，可由主线程在 purchase/sales/mrp 审核事务内直接调 `QcInspectService#pendingOfSource`。

### 5.3 不合格品处置与后续动作（待联调）

处置决定仅记录质量口径，不自动触发库存/财务事务：
- 退货（decision=1）：若需生成采购退货单，由 purchase 模块按 reject 单引用实现（联调项）；
- 返工（2）/报废（4）：若需扣减库存或生成生产返工单，由 inventory/mrp 后续接线；
- 让步接收（3）：仅记录，允许入库使用。

## 6. API 清单（context-path=/api）

### 检验模板 /qc/templates
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /qc/templates | 分页：pageNum/pageSize/keyword(编码/名称模糊)/inspectType/status |
| GET | /qc/templates/{id} | 详情（含检验项目子表+分类名称） |
| POST | /qc/templates | 新增（items 至少一行，编码唯一，下限≤上限，抽检比例 0-100） |
| PUT | /qc/templates/{id} | 修改（检验项目整体覆盖式重建） |
| DELETE | /qc/templates/{id} | 删除（逻辑删；检验单明细为快照不受影响） |

### 检验单 /qc/inspects
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /qc/inspects | 分页：keyword(检验单号/来源单号)/inspectType/sourceType/materialId/conclusion/status/dateBegin/dateEnd |
| GET | /qc/inspects/{id} | 详情（含检验项目实检值明细、物料/供应商/模板名称） |
| POST | /qc/inspects | 手工新增（草稿；items 不传则按模板自动带出） |
| PUT | /qc/inspects/{id} | 修改草稿（表头+明细整体覆盖） |
| PUT | /qc/inspects/{id}/submit | **提交检验**：实检值/单项判定/合格数/不合格数/结论/检验人/检验日期，0→10 |
| DELETE | /qc/inspects/{id} | 删除（仅草稿，逻辑删） |
| POST | /qc/inspects/from/{bizType}/{bizId} | **挂点**：按来源单自动生成检验单（幂等） |
| GET | /qc/inspects/pending?bizType&bizId | **挂点**：来源单未完成检验卡点查询（见 5.2） |

### 不合格品处置 /qc/rejects
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /qc/rejects | 分页：keyword(处置单号/检验单号)/materialId/decision |
| GET | /qc/rejects/{id} | 详情 |
| GET | /qc/rejects/sources | 可处置检验单选项（已检验、不合格数>0、扣除已处置仍有余额） |
| POST | /qc/rejects | 新增（数量守卫：累计 ≤ 不合格数） |
| PUT | /qc/rejects/{id} | 修改（不允许变更来源检验单） |
| DELETE | /qc/rejects/{id} | 删除（逻辑删，余额自动释放） |

### 统计 /qc/statistics
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /qc/statistics?from&to | 按物料/供应商合格率（Σ合格/Σ抽检 ×100，HALF_UP 2 位，仅已检验单按检验日期）+ 按天趋势（区间补零）；from/to 缺省最近 30 天 |

## 7. 前端

- `src/api/qc.ts`：类型与请求（与后端 VO 对齐）；
- `src/pages/qc/inspects/InspectList.tsx`：列表（类型/来源/状态/结论/日期筛选）+ 从来源单生成弹窗 + 手工新增弹窗 + 检验录入 Drawer（明细行实检值+单项判定+结论，数量口径即时校验）+ 详情 Drawer；
- `src/pages/qc/rejects/RejectList.tsx`：列表 + 新增/修改弹窗（来源检验单下拉带出剩余可处置数量）+ 详情 Drawer；
- `src/pages/qc/templates/TemplateList.tsx`：列表 + 新增/修改弹窗（检验项目子表增删行）；
- `src/pages/qc/shared.tsx`：类型/状态/结论/处置决定标签映射、基础资料选项 hooks、数量格式化；
- `src/router/modules/qc.tsx`：`qcRoutes`（qc/inspects、qc/rejects、qc/templates，path 无首斜杠），菜单已在 MainLayout 预置，主线程合并路由片段。

## 8. 已知事项 / 待联调

1. 检验人（inspector_name）与审批人（approver）为姓名文本：与一期 `audit_by=0` 系统占位同口径，
   JWT 用户上下文接入后可切换为用户 ID 关联（表结构无需变更，仅换字段语义）；
2. 一期单据审核卡点尚未在 purchase/sales/mrp 侧接线（见 5.2，接口已就绪）；
3. 不合格处置不自动联动采购退货/库存扣减（见 5.3，按金蝶口径先记录质量决定）；
4. 日期区间筛选按检验日期（inspect_date），草稿单无检验日期会被区间过滤排除（符合口径）。
