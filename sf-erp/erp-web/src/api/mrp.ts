/**
 * 生产制造模块 API —— 类型定义与请求函数（与后端 VO 一一对应）。
 */
import { get, post, put, del, type PageResult } from '@/api/request'

// ==================== 公共类型 ====================

/** 物料简要信息（选择器） */
export interface MaterialBrief {
  id: number
  code: string
  name: string
  spec: string | null
  unit: string | null
}

/** 仓库简要信息 */
export interface WarehouseBrief {
  id: number
  name: string
}

/** 供应商简要信息 */
export interface SupplierBrief {
  id: number
  name: string
}

// ==================== BOM ====================

/** BOM 列表/详情行 */
export interface BomRow {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  unit: string | null
  version: string
  status: 'DRAFT' | 'ACTIVE' | 'DISABLED'
  remark: string | null
  createTime: string
  itemCount: number
  items?: BomItemRow[]
}

/** BOM 子件行 */
export interface BomItemRow {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  spec: string | null
  unit: string | null
  qty: number
  lossRate: number
  remark: string | null
}

/** BOM 子件替代料行（V621） */
export interface BomSubstituteRow {
  id: number
  bomItemId: number
  bomId: number
  mainMaterialId: number
  mainMaterialCode: string | null
  mainMaterialName: string | null
  subMaterialId: number
  subMaterialCode: string | null
  subMaterialName: string | null
  unit: string | null
  priority: number
  ratio: number
  remark: string | null
}

/** 替代料提交项 */
export interface BomSubstituteItemForm {
  subMaterialId: number
  priority: number
  ratio: number
  remark?: string
}

/** BOM 树节点 */
export interface BomTreeNode {
  materialId: number
  code: string | null
  name: string
  unit: string | null
  level: number
  qty: number
  lossRate: number
  bomVersion: string | null
  children: BomTreeNode[]
}

/** BOM 表单 */
export interface BomForm {
  materialId: number
  version: string
  remark?: string
  items: { materialId: number; qty: number; lossRate: number; remark?: string }[]
}

// ==================== MPS ====================

/** MPS 行 */
export interface MpsRow {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  unit: string | null
  qty: number
  startDate: string
  status: 'PENDING' | 'CALCULATED'
  remark: string | null
  createTime: string
}

/** MPS 表单 */
export interface MpsForm {
  materialId: number
  qty: number
  startDate: string
  remark?: string
}

// ==================== MRP 运算 ====================

/** 运算批次行 */
export interface CalcBatchRow {
  id: number
  batchNo: string
  mpsCount: number
  suggestionCount: number
  remark: string | null
  createTime: string
}

/** 运算结果摘要 */
export interface MrpRunResult {
  batchNo: string
  mpsCount: number
  detailCount: number
  suggestionCount: number
}

/** 展开明细树节点 */
export interface CalcDetailTreeNode {
  nodePath: string
  level: number
  materialId: number
  code: string | null
  name: string
  unit: string | null
  grossQty: number
  stockQty: number
  onwayQty: number
  netQty: number
  suggestQty: number | null
  suggestDate: string | null
  planType: 'PURCHASE' | 'PRODUCE' | null
  children: CalcDetailTreeNode[]
}

/** 批次全树明细 */
export interface BatchDetail {
  batchNo: string
  trees: CalcDetailTreeNode[]
}

/** 发补建议行 */
export interface SuggestionRow {
  id: number
  batchNo: string
  planType: 'PURCHASE' | 'PRODUCE'
  materialId: number
  materialCode: string | null
  materialName: string | null
  unit: string | null
  qty: number
  suggestDate: string
  level: number
  status: 'PENDING' | 'CONVERTED'
  refType: string | null
  refId: number | null
  createTime: string
}

// ==================== 生产订单 / 领料 / 委外 ====================

/** 生产订单行 */
export interface WorkOrderRow {
  id: number
  orderNo: string
  materialId: number
  materialCode: string | null
  materialName: string | null
  unit: string | null
  qty: number
  bomId: number
  bomVersion: string
  warehouseId: number | null
  pickType: 'DIRECT' | 'BACKFLUSH' | null
  backflushWarehouseId: number | null
  planStartDate: string | null
  planEndDate: string | null
  status: 'DRAFT' | 'RELEASED' | 'PRODUCING' | 'FINISHED' | 'CLOSED'
  finishQty: number
  badQty: number
  sourceType: string | null
  sourceId: number | null
  remark: string | null
  createTime: string
}

/** 生产订单表单 */
export interface WorkOrderForm {
  materialId: number
  qty: number
  warehouseId?: number
  pickType?: 'DIRECT' | 'BACKFLUSH'
  backflushWarehouseId?: number
  planStartDate?: string
  planEndDate?: string
  remark?: string
}

/** 领料单行 */
export interface MaterialIssueRow {
  id: number
  issueNo: string
  workOrderId: number
  warehouseId: number
  status: 'DRAFT' | 'AUDITED'
  remark: string | null
  createTime: string
  items: MaterialIssueItemRow[]
}

/** 领料明细行 */
export interface MaterialIssueItemRow {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  unit: string | null
  shouldQty: number
  actualQty: number
}

/** 委外单行 */
export interface OutsourceRow {
  id: number
  orderNo: string
  supplierId: number
  supplierName: string | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  qty: number
  bomId: number
  bomVersion: string
  issueWarehouseId: number | null
  inWarehouseId: number | null
  feeUnitPrice: number
  status: 'DRAFT' | 'ISSUED' | 'RECEIVED'
  remark: string | null
  createTime: string
  items?: { id: number; materialId: number; materialCode: string | null; materialName: string | null; unit: string | null; qty: number }[]
}

// ==================== 请求函数 ====================

/** 支撑数据（选择器） */
export const mrpApi = {
  materials: (params: { pageNum?: number; pageSize?: number; keyword?: string }) =>
    get<PageResult<MaterialBrief>>('/mrp/materials', params),
  warehouses: () => get<WarehouseBrief[]>('/mrp/warehouses'),
  suppliers: (keyword?: string) => get<SupplierBrief[]>('/mrp/suppliers', { keyword }),
}

/** BOM */
export const bomApi = {
  page: (params: { pageNum: number; pageSize: number; keyword?: string; status?: string }) =>
    get<PageResult<BomRow>>('/mrp/boms', params),
  detail: (id: number) => get<BomRow>(`/mrp/boms/${id}`),
  create: (form: BomForm) => post<number>('/mrp/boms', form),
  update: (id: number, form: BomForm) => put<void>(`/mrp/boms/${id}`, form),
  remove: (id: number) => del<void>(`/mrp/boms/${id}`),
  copy: (id: number, newVersion: string) => post<number>(`/mrp/boms/${id}/copy`, { newVersion }),
  audit: (id: number) => post<void>(`/mrp/boms/${id}/audit`),
  disable: (id: number) => post<void>(`/mrp/boms/${id}/disable`),
  tree: (id: number) => get<BomTreeNode>(`/mrp/boms/${id}/tree`),
}

/** BOM 替代料（V621） */
export const bomSubstituteApi = {
  listByBom: (bomId: number) =>
    get<Record<string, BomSubstituteRow[]>>(`/mrp/bom-substitutes`, { bomId }),
  saveBatch: (bomItemId: number, items: BomSubstituteItemForm[]) =>
    post<void>(`/mrp/bom-substitutes/items/${bomItemId}`, { items }),
  remove: (id: number) => del<void>(`/mrp/bom-substitutes/${id}`),
}

/** MPS */
export const mpsApi = {
  page: (params: { pageNum: number; pageSize: number; keyword?: string; status?: string }) =>
    get<PageResult<MpsRow>>('/mrp/mps', params),
  create: (form: MpsForm) => post<number>('/mrp/mps', form),
  update: (id: number, form: MpsForm) => put<void>(`/mrp/mps/${id}`, form),
  remove: (id: number) => del<void>(`/mrp/mps/${id}`),
}

/** MRP 运算 */
export const calcApi = {
  run: (body: { mpsIds?: number[]; remark?: string }) =>
    post<MrpRunResult>('/mrp/calc/run', body),
  batches: (params: { pageNum: number; pageSize: number }) =>
    get<PageResult<CalcBatchRow>>('/mrp/calc/batches', params),
  batchDetails: (batchNo: string) => get<BatchDetail>(`/mrp/calc/batches/${batchNo}/details`),
}

/** MRP 建议 */
export const suggestionApi = {
  page: (params: {
    pageNum: number
    pageSize: number
    batchNo?: string
    planType?: string
    status?: string
  }) => get<PageResult<SuggestionRow>>('/mrp/suggestions', params),
  toWorkOrder: (id: number, warehouseId?: number) =>
    post<number>(`/mrp/suggestions/${id}/to-work-order`, { warehouseId }),
  toPurchase: (id: number, body?: { supplierId?: number; warehouseId?: number }) =>
    post<number>(`/mrp/suggestions/${id}/to-purchase`, body ?? {}),
  toPurchaseBatch: (ids: number[], body?: { supplierId?: number; warehouseId?: number }) =>
    post<number>('/mrp/suggestions/to-purchase-batch', { ids, ...body }),
}

/** 生产订单 */
export const workOrderApi = {
  page: (params: { pageNum: number; pageSize: number; keyword?: string; status?: string }) =>
    get<PageResult<WorkOrderRow>>('/mrp/work-orders', params),
  detail: (id: number) => get<WorkOrderRow>(`/mrp/work-orders/${id}`),
  create: (form: WorkOrderForm) => post<number>('/mrp/work-orders', form),
  update: (id: number, form: WorkOrderForm) => put<void>(`/mrp/work-orders/${id}`, form),
  remove: (id: number) => del<void>(`/mrp/work-orders/${id}`),
  release: (id: number) => post<void>(`/mrp/work-orders/${id}/release`),
  finish: (id: number, body: { goodQty: number; badQty: number; warehouseId: number }) =>
    post<void>(`/mrp/work-orders/${id}/finish`, body),
  close: (id: number) => post<void>(`/mrp/work-orders/${id}/close`),
}

/** 领料单 */
export const issueApi = {
  page: (params: { pageNum: number; pageSize: number; workOrderId?: number; status?: string }) =>
    get<PageResult<MaterialIssueRow>>('/mrp/material-issues', params),
  detail: (id: number) => get<MaterialIssueRow>(`/mrp/material-issues/${id}`),
  create: (body: { workOrderId: number; warehouseId: number; items?: { materialId: number; actualQty: number }[]; remark?: string }) =>
    post<number>('/mrp/material-issues', body),
  update: (id: number, body: { workOrderId: number; warehouseId: number; items?: { materialId: number; actualQty: number }[]; remark?: string }) =>
    put<void>(`/mrp/material-issues/${id}`, body),
  audit: (id: number) => post<void>(`/mrp/material-issues/${id}/audit`),
  remove: (id: number) => del<void>(`/mrp/material-issues/${id}`),
}

/** 委外单 */
export const outsourceApi = {
  page: (params: { pageNum: number; pageSize: number; keyword?: string; status?: string }) =>
    get<PageResult<OutsourceRow>>('/mrp/outsources', params),
  detail: (id: number) => get<OutsourceRow>(`/mrp/outsources/${id}`),
  create: (body: {
    supplierId: number
    materialId: number
    qty: number
    issueWarehouseId?: number
    inWarehouseId?: number
    feeUnitPrice: number
    remark?: string
  }) => post<number>('/mrp/outsources', body),
  remove: (id: number) => del<void>(`/mrp/outsources/${id}`),
  issueMaterial: (id: number) => post<void>(`/mrp/outsources/${id}/issue`),
  receive: (id: number, body: { goodQty: number; warehouseId?: number }) =>
    post<void>(`/mrp/outsources/${id}/receive`, body),
}

// ==================== 二期：工作中心 / 工序 / 工艺路线 ====================

/** 工作中心行 */
export interface WorkCenterRow {
  id: number
  code: string
  name: string
  workshop: string | null
  capacityHoursPerDay: number
  equipmentCount: number
  status: 'ENABLED' | 'DISABLED'
  remark: string | null
  createTime: string
}

/** 工作中心表单 */
export interface WorkCenterForm {
  name: string
  workshop?: string
  capacityHoursPerDay: number
  equipmentCount: number
  status?: string
  remark?: string
}

/** 工序定义行 */
export interface OperationRow {
  id: number
  code: string
  name: string
  defaultWorkCenterId: number | null
  defaultWorkCenterCode: string | null
  defaultWorkCenterName: string | null
  setupMinutes: number
  unitMinutes: number
  outsourceFlag: number
  status: 'ENABLED' | 'DISABLED'
  remark: string | null
  createTime: string
}

/** 工序表单 */
export interface OperationForm {
  name: string
  defaultWorkCenterId?: number
  setupMinutes?: number
  unitMinutes: number
  outsourceFlag?: number
  status?: string
  remark?: string
}

/** 工艺路线行（列表含步骤数，详情含步骤明细） */
export interface RoutingRow {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  unit: string | null
  version: string
  status: 'DRAFT' | 'ACTIVE' | 'DISABLED'
  remark: string | null
  createTime: string
  stepCount: number
  steps?: RoutingStepRow[]
}

/** 工艺路线步骤行（effective* 为覆盖工序默认后的生效值） */
export interface RoutingStepRow {
  id: number
  seqNo: number
  operationId: number
  operationCode: string | null
  operationName: string | null
  outsourceFlag: number | null
  workCenterId: number | null
  workCenterCode: string | null
  workCenterName: string | null
  unitMinutes: number | null
  effectiveUnitMinutes: number | null
  reportFlag: number
  remark: string | null
}

/** 工艺路线工序序列（树形查看） */
export interface RoutingTree {
  routingId: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  version: string
  status: string
  steps: {
    seqNo: number
    operationCode: string | null
    operationName: string | null
    workCenterCode: string | null
    workCenterName: string | null
    effectiveUnitMinutes: number | null
    reportFlag: number
  }[]
}

/** 工艺路线表单（步骤整体提交） */
export interface RoutingForm {
  materialId: number
  version: string
  remark?: string
  steps: {
    seqNo: number
    operationId: number
    workCenterId?: number
    unitMinutes?: number
    reportFlag?: number
    remark?: string
  }[]
}

/** 工作中心 */
export const workCenterApi = {
  page: (params: { pageNum: number; pageSize: number; keyword?: string; status?: string }) =>
    get<PageResult<WorkCenterRow>>('/mrp/work-centers', params),
  listAll: () => get<WorkCenterRow[]>('/mrp/work-centers/all'),
  create: (form: WorkCenterForm) => post<number>('/mrp/work-centers', form),
  update: (id: number, form: WorkCenterForm) => put<void>(`/mrp/work-centers/${id}`, form),
  remove: (id: number) => del<void>(`/mrp/work-centers/${id}`),
}

/** 工序定义 */
export const operationApi = {
  page: (params: { pageNum: number; pageSize: number; keyword?: string; status?: string }) =>
    get<PageResult<OperationRow>>('/mrp/operations', params),
  listAll: () => get<OperationRow[]>('/mrp/operations/all'),
  create: (form: OperationForm) => post<number>('/mrp/operations', form),
  update: (id: number, form: OperationForm) => put<void>(`/mrp/operations/${id}`, form),
  remove: (id: number) => del<void>(`/mrp/operations/${id}`),
}

/** 工艺路线 */
export const routingApi = {
  page: (params: { pageNum: number; pageSize: number; keyword?: string; status?: string }) =>
    get<PageResult<RoutingRow>>('/mrp/routings', params),
  detail: (id: number) => get<RoutingRow>(`/mrp/routings/${id}`),
  tree: (id: number) => get<RoutingTree>(`/mrp/routings/${id}/tree`),
  create: (form: RoutingForm) => post<number>('/mrp/routings', form),
  update: (id: number, form: RoutingForm) => put<void>(`/mrp/routings/${id}`, form),
  remove: (id: number) => del<void>(`/mrp/routings/${id}`),
  copy: (id: number, newVersion: string) => post<number>(`/mrp/routings/${id}/copy`, { newVersion }),
  audit: (id: number) => post<void>(`/mrp/routings/${id}/audit`),
  disable: (id: number) => post<void>(`/mrp/routings/${id}/disable`),
}

// ==================== 二期：派工 / 报工 / 能力负荷 ====================

/** 工单工序任务行（看板卡片） */
export interface WoStepRow {
  id: number
  workOrderId: number
  orderNo: string | null
  materialCode: string | null
  materialName: string | null
  seqNo: number
  operationId: number
  operationCode: string | null
  operationName: string | null
  workCenterId: number | null
  workCenterCode: string | null
  workCenterName: string | null
  unitMinutes: number
  planQty: number
  finishQty: number
  badQty: number
  progress: number
  planStartTime: string | null
  planEndTime: string | null
  status: 'PENDING_DISPATCH' | 'DISPATCHED' | 'PROCESSING' | 'FINISHED'
  assignee: string | null
  dispatchTime: string | null
  actualMinutes: number
  qcPendingNote: string | null
}

/** 派工看板（按工作中心分组） */
export interface DispatchBoard {
  groups: {
    workCenterId: number | null
    workCenterCode: string | null
    workCenterName: string | null
    capacityHoursPerDay: number | null
    stepCount: number
    steps: WoStepRow[]
  }[]
}

/** 工序报工明细行 */
export interface WorkReportRow {
  id: number
  woStepId: number
  workOrderId: number
  orderNo: string | null
  operationId: number
  operationName: string | null
  employee: string
  goodQty: number
  badQty: number
  workMinutes: number
  qcNote: string | null
  reportTime: string
}

/** 能力负荷行 */
export interface CapacityLoadRow {
  workCenterId: number
  code: string
  name: string
  workshop: string | null
  status: string
  loadHours: number
  availableHours: number
  loadRate: number | null
  overloaded: boolean
  pendingStepCount: number
}

/** 能力负荷结果 */
export interface CapacityLoadResult {
  fromDate: string
  toDate: string
  workdays: number
  rows: CapacityLoadRow[]
  overloads: CapacityLoadRow[]
}

/** 派工 */
export const dispatchApi = {
  board: (params: { status?: string; workCenterId?: number }) =>
    get<DispatchBoard>('/mrp/dispatch/list', params),
  woSteps: (workOrderId: number) => get<WoStepRow[]>('/mrp/dispatch/wo-steps', { workOrderId }),
  dispatch: (woStepId: number, body: { assignee: string; planStartTime?: string }) =>
    post<void>(`/mrp/dispatch/${woStepId}`, body),
}

/** 工序报工 */
export const workReportApi = {
  report: (body: {
    woStepId: number
    employee: string
    goodQty: number
    badQty: number
    workMinutes?: number
    qcNote?: string
  }) => post<number>('/mrp/report', body),
  list: (params: { woStepId?: number; workOrderId?: number }) =>
    get<WorkReportRow[]>('/mrp/report/list', params),
}

/** 能力计划（CRP 简版） */
export const capacityApi = {
  load: (params: { from?: string; to?: string; workCenterId?: number }) =>
    get<CapacityLoadResult>('/mrp/capacity/load', params),
}
