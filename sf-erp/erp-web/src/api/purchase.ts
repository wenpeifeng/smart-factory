import { get, post, put, del, type PageResult } from './request'

/**
 * 采购管理模块 API 定义（采购订单 / 采购入库 / 采购退货 / 供应商对账）。
 * 出参类型与后端 VO 严格对齐；金额/数量后端 DECIMAL，JSON 序列化为 number。

 * ========= 基础资料选项（basedata 标准接口并行开发中，暂走采购模块只读代理） =========
 */
export interface BaseOption {
  id: number
  code: string
  name: string
  /** 仅物料有值 */
  spec?: string | null
  /** 仅物料有值 */
  unit?: string | null
}

export const optionsApi = {
  suppliers: () => get<BaseOption[]>('/purchase/options/suppliers'),
  materials: () => get<BaseOption[]>('/purchase/options/materials'),
  warehouses: () => get<BaseOption[]>('/purchase/options/warehouses'),
}

/** 通用单据状态（入库/退货）：0草稿 10已审核 */
export type DocStatus = 0 | 10

/** 采购订单状态：0草稿 10已审核 20部分入库 30已完成 90已关闭 */
export type OrderStatus = 0 | 10 | 20 | 30 | 90

/** 单据列表通用查询参数 */
export interface DocPageParams {
  pageNum: number
  pageSize: number
  keyword?: string
  supplierId?: number
  status?: number
  dateBegin?: string
  dateEnd?: string
}

/** 查询参数 -> axios params（request.get 只收 Record，这里显式转换保持类型边界清晰） */
function toParams(params: DocPageParams): Record<string, unknown> {
  return {
    pageNum: params.pageNum,
    pageSize: params.pageSize,
    keyword: params.keyword,
    supplierId: params.supplierId,
    status: params.status,
    dateBegin: params.dateBegin,
    dateEnd: params.dateEnd,
  }
}

/* ==================== 采购订单 ==================== */

export interface PurOrderRow {
  id: number
  orderNo: string
  orderDate: string
  supplierId: number
  supplierName: string | null
  warehouseId: number | null
  warehouseName: string | null
  totalAmount: number
  status: OrderStatus
  auditTime: string | null
  remark: string | null
  createTime: string
}

export interface PurOrderDetailRow {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  spec: string | null
  unit: string | null
  qty: number
  price: number
  taxRate: number
  amount: number
  deliveryDate: string | null
  receivedQty: number
  remark: string | null
}

export interface PurOrderDetail extends PurOrderRow {
  details: PurOrderDetailRow[]
}

/** 订单明细提交行（金额由后端计算，不传） */
export interface PurOrderLineInput {
  materialId: number
  qty: number
  price: number
  taxRate?: number
  deliveryDate?: string
  remark?: string
}

/** 订单新增/修改提交体 */
export interface PurOrderSavePayload {
  supplierId: number
  warehouseId?: number
  orderDate: string
  remark?: string
  details: PurOrderLineInput[]
}

export const orderApi = {
  page: (params: DocPageParams) => get<PageResult<PurOrderRow>>('/purchase/orders', toParams(params)),
  get: (id: number) => get<PurOrderDetail>(`/purchase/orders/${id}`),
  create: (payload: PurOrderSavePayload) => post<number>('/purchase/orders', payload),
  update: (id: number, payload: PurOrderSavePayload) => put<void>(`/purchase/orders/${id}`, payload),
  audit: (id: number) => put<void>(`/purchase/orders/${id}/audit`),
  close: (id: number) => put<void>(`/purchase/orders/${id}/close`),
  remove: (id: number) => del<void>(`/purchase/orders/${id}`),
}

/* ==================== 采购入库单 ==================== */

export interface PurInstockRow {
  id: number
  instockNo: string
  orderId: number
  orderNo: string | null
  supplierId: number
  supplierName: string | null
  warehouseId: number
  warehouseName: string | null
  instockDate: string
  totalAmount: number
  status: DocStatus
  auditTime: string | null
  remark: string | null
  createTime: string
}

export interface PurInstockDetailRow {
  id: number
  orderDetailId: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  spec: string | null
  unit: string | null
  qty: number
  price: number
  taxRate: number
  amount: number
  returnedQty: number
  remark: string | null
}

export interface PurInstockDetail extends PurInstockRow {
  details: PurInstockDetailRow[]
}

export interface PurInstockLineInput {
  orderDetailId: number
  materialId: number
  qty: number
  price: number
  taxRate?: number
  remark?: string
}

export interface PurInstockSavePayload {
  orderId: number
  supplierId: number
  warehouseId: number
  instockDate: string
  remark?: string
  details: PurInstockLineInput[]
}

/** 入库选单预填充（按已审核采购订单） */
export interface InstockPreview {
  orderId: number
  orderNo: string
  supplierId: number
  supplierName: string | null
  warehouseId: number | null
  warehouseName: string | null
  orderDate: string
  details: {
    orderDetailId: number
    materialId: number
    materialCode: string | null
    materialName: string | null
    spec: string | null
    unit: string | null
    price: number
    taxRate: number
    orderQty: number
    receivedQty: number
    suggestQty: number
    deliveryDate: string | null
  }[]
}

export const instockApi = {
  page: (params: DocPageParams) => get<PageResult<PurInstockRow>>('/purchase/instock', toParams(params)),
  get: (id: number) => get<PurInstockDetail>(`/purchase/instock/${id}`),
  previewFromOrder: (orderId: number) =>
    get<InstockPreview>(`/purchase/instock/from-order/${orderId}`),
  create: (payload: PurInstockSavePayload) => post<number>('/purchase/instock', payload),
  update: (id: number, payload: PurInstockSavePayload) => put<void>(`/purchase/instock/${id}`, payload),
  audit: (id: number) => put<void>(`/purchase/instock/${id}/audit`),
  remove: (id: number) => del<void>(`/purchase/instock/${id}`),
}

/* ==================== 采购退货单 ==================== */

export interface PurReturnRow {
  id: number
  returnNo: string
  instockId: number
  instockNo: string | null
  supplierId: number
  supplierName: string | null
  warehouseId: number
  warehouseName: string | null
  returnDate: string
  totalAmount: number
  status: DocStatus
  auditTime: string | null
  remark: string | null
  createTime: string
}

export interface PurReturnDetailRow {
  id: number
  instockDetailId: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  spec: string | null
  unit: string | null
  qty: number
  price: number
  taxRate: number
  amount: number
  remark: string | null
}

export interface PurReturnDetail extends PurReturnRow {
  details: PurReturnDetailRow[]
}

export interface PurReturnLineInput {
  instockDetailId: number
  materialId: number
  qty: number
  price: number
  taxRate?: number
  remark?: string
}

export interface PurReturnSavePayload {
  instockId: number
  supplierId: number
  warehouseId: number
  returnDate: string
  remark?: string
  details: PurReturnLineInput[]
}

/** 退货选单预填充（按已审核入库单） */
export interface ReturnPreview {
  instockId: number
  instockNo: string
  supplierId: number
  supplierName: string | null
  warehouseId: number | null
  warehouseName: string | null
  instockDate: string
  details: {
    instockDetailId: number
    materialId: number
    materialCode: string | null
    materialName: string | null
    spec: string | null
    unit: string | null
    price: number
    taxRate: number
    instockQty: number
    returnedQty: number
    suggestQty: number
  }[]
}

export const returnApi = {
  page: (params: DocPageParams) => get<PageResult<PurReturnRow>>('/purchase/returns', toParams(params)),
  get: (id: number) => get<PurReturnDetail>(`/purchase/returns/${id}`),
  previewFromInstock: (instockId: number) =>
    get<ReturnPreview>(`/purchase/returns/from-instock/${instockId}`),
  create: (payload: PurReturnSavePayload) => post<number>('/purchase/returns', payload),
  update: (id: number, payload: PurReturnSavePayload) => put<void>(`/purchase/returns/${id}`, payload),
  audit: (id: number) => put<void>(`/purchase/returns/${id}/audit`),
  remove: (id: number) => del<void>(`/purchase/returns/${id}`),
}

/* ==================== 供应商对账单（只读统计） ==================== */

export interface SupplierStatement {
  supplierId: number
  supplierName: string | null
  month: string
  instockAmount: number
  returnAmount: number
  netAmount: number
  instockCount: number
  returnCount: number
}

export const statementApi = {
  get: (supplierId: number, month: string) =>
    get<SupplierStatement>('/purchase/statements', { supplierId, month }),
}

/* ==================== 请购单（V2111：请购 -> 询比价 -> 采购订单链路源头） ==================== */

/** 请购单状态：0草稿 10已提交 20已转采购 30已关闭 */
export type RequisitionStatus = 0 | 10 | 20 | 30

export interface RequisitionPageParams {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number
  deptId?: number
  materialId?: number
}

export interface RequisitionRow {
  id: number
  reqNo: string
  deptId: number
  deptName: string | null
  applicantId: number | null
  applicantName: string | null
  expectDate: string
  status: RequisitionStatus
  remark: string | null
  createTime: string
}

export interface RequisitionItemRow {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  spec: string | null
  unit: string | null
  qty: number
  orderedQty: number
  requireDate: string | null
  remark: string | null
}

export interface RequisitionDetail extends RequisitionRow {
  items: RequisitionItemRow[]
}

export interface RequisitionLineInput {
  materialId: number
  qty: number
  requireDate?: string
  remark?: string
}

export interface RequisitionSavePayload {
  deptId: number
  applicantId?: number
  expectDate: string
  remark?: string
  items: RequisitionLineInput[]
}

export const requisitionApi = {
  page: (params: RequisitionPageParams) =>
    get<PageResult<RequisitionRow>>('/purchase/requisitions', { ...params }),
  get: (id: number) => get<RequisitionDetail>(`/purchase/requisitions/${id}`),
  deptOptions: () => get<BaseOption[]>('/purchase/requisitions/dept-options'),
  create: (payload: RequisitionSavePayload) => post<number>('/purchase/requisitions', payload),
  update: (id: number, payload: RequisitionSavePayload) =>
    put<void>(`/purchase/requisitions/${id}`, payload),
  submit: (id: number) => put<void>(`/purchase/requisitions/${id}/submit`),
  close: (id: number) => put<void>(`/purchase/requisitions/${id}/close`),
  remove: (id: number) => del<void>(`/purchase/requisitions/${id}`),
}

/* ==================== 询比价单 ==================== */

/** 询比价单状态：0询价中 10已定标 30已取消 */
export type RfqStatus = 0 | 10 | 30

/** 询比价供应商行状态：0已邀请 10已报价 20中标 */
export type RfqSupplierStatus = 0 | 10 | 20

export interface RfqPageParams {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number
  requisitionId?: number
}

export interface RfqRow {
  id: number
  rfqNo: string
  requisitionId: number | null
  reqNo: string | null
  deadline: string | null
  status: RfqStatus
  awardTime: string | null
  remark: string | null
  createTime: string
}

export interface RfqSupplierRow {
  id: number
  supplierId: number
  supplierName: string | null
  quoteAmount: number
  status: RfqSupplierStatus
}

export interface RfqItemRow {
  id: number
  requisitionItemId: number | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  spec: string | null
  unit: string | null
  qty: number
  awardSupplierId: number | null
  awardPrice: number | null
  awardLeadDays: number | null
  purOrderId: number | null
  remark: string | null
}

export interface RfqDetail extends RfqRow {
  suppliers: RfqSupplierRow[]
  items: RfqItemRow[]
}

export interface RfqLineInput {
  requisitionItemId?: number
  materialId: number
  qty: number
  remark?: string
}

export interface RfqSavePayload {
  requisitionId?: number
  deadline: string
  supplierIds: number[]
  remark?: string
  items: RfqLineInput[]
}

/** 报价录入（按供应商整体提交，重录=覆盖） */
export interface RfqQuotePayload {
  supplierId: number
  quotes: { itemId: number; price: number; leadDays: number }[]
}

/** 分项定标 */
export interface RfqAwardPayload {
  perItem: { itemId: number; supplierId: number }[]
}

/** 比价矩阵：物料×供应商 */
export interface RfqComparison {
  rfqId: number
  rfqNo: string
  status: RfqStatus
  deadline: string | null
  suppliers: {
    supplierId: number
    supplierName: string | null
    status: RfqSupplierStatus
    quoteAmount: number
  }[]
  items: {
    itemId: number
    materialCode: string | null
    materialName: string | null
    spec: string | null
    unit: string | null
    qty: number
    awardSupplierId: number | null
    quotes: {
      supplierId: number
      price: number
      leadDays: number
      total: number
      lowest: boolean
    }[]
  }[]
}

/** 下推采购订单结果（一供应商一单） */
export interface RfqToOrderResult {
  rfqNo: string
  orders: {
    supplierId: number
    supplierName: string | null
    orderId: number
    orderNo: string
  }[]
}

export const rfqApi = {
  page: (params: RfqPageParams) => get<PageResult<RfqRow>>('/purchase/rfqs', { ...params }),
  get: (id: number) => get<RfqDetail>(`/purchase/rfqs/${id}`),
  comparison: (id: number) => get<RfqComparison>(`/purchase/rfqs/${id}/comparison`),
  create: (payload: RfqSavePayload) => post<number>('/purchase/rfqs', payload),
  update: (id: number, payload: RfqSavePayload) => put<void>(`/purchase/rfqs/${id}`, payload),
  saveQuotes: (id: number, payload: RfqQuotePayload) =>
    post<void>(`/purchase/rfqs/${id}/quotes`, payload),
  award: (id: number, payload: RfqAwardPayload) => post<void>(`/purchase/rfqs/${id}/award`, payload),
  toOrders: (id: number) => post<RfqToOrderResult>(`/purchase/rfqs/${id}/to-orders`),
  cancel: (id: number) => put<void>(`/purchase/rfqs/${id}/cancel`),
  remove: (id: number) => del<void>(`/purchase/rfqs/${id}`),
}

/* ==================== 采购暂估应付（货到票未到，V231） ==================== */

/** 暂估单状态：0暂估中 10已冲回 20部分冲回 */
export type EstimationStatus = 0 | 10 | 20

export interface EstimationRow {
  id: number
  estimationNo: string
  instockId: number
  instockNo: string
  supplierId: number
  supplierName: string | null
  amount: number
  reversedAmount: number
  openAmount: number
  status: EstimationStatus
  period: string
  invoiceNo: string | null
  remark: string | null
  createTime: string
}

export interface EstimationPageParams {
  pageNum: number
  pageSize: number
  keyword?: string
  supplierId?: number
  status?: number
  period?: string
}

/** 暂估报表（按期间 yyyyMM 汇总，供应商维度） */
export interface EstimationSummary {
  period: string
  totalAmount: number
  reversedAmount: number
  openAmount: number
  suppliers: {
    supplierId: number
    supplierName: string | null
    estimationCount: number
    totalAmount: number
    reversedAmount: number
    openAmount: number
  }[]
}

/** 暂估手动冲回表单（actualAmount=发票实际金额，价税合计口径） */
export interface EstimationReversePayload {
  invoiceNo: string
  actualAmount: number
}

export const estimationApi = {
  page: (params: EstimationPageParams) =>
    get<PageResult<EstimationRow>>('/purchase/estimations', { ...params }),
  summary: (month?: string) =>
    get<EstimationSummary>('/purchase/estimations/summary', month ? { month } : undefined),
  createFromInstock: (instockId: number) =>
    post<number>(`/purchase/estimations/from-instock/${instockId}`),
  reverse: (id: number, payload: EstimationReversePayload) =>
    post<void>(`/purchase/estimations/${id}/reverse`, payload),
}

// ========= 供应商交易评分（V141，后端在 basedata 模块、权限挂采购口径） =========

/** 供应商评分行（对应后端 SupplierScoreVO） */
export interface SupplierScoreRow {
  id: number
  supplierId: number
  supplierCode: string | null
  supplierName: string | null
  periodType: 'MONTH' | 'QUARTER'
  period: string
  periodStart: string
  periodEnd: string
  orderCount: number
  orderAmount: number
  instockCount: number
  instockQty: number
  ontimeRate: number
  qualifiedRate: number
  returnRate: number
  totalScore: number
  grade: 'A' | 'B' | 'C' | 'D'
  calcTime: string
}

/** 评级汇总报表（对应后端 SupplierGradeSummaryVO） */
export interface SupplierGradeSummary {
  periodType: string
  period: string | null
  supplierCount: number
  gradeA: number
  gradeB: number
  gradeC: number
  gradeD: number
  avgScore: number
  totalOrderAmount: number
}

export const supplierScoreApi = {
  page: (params: {
    pageNum: number
    pageSize: number
    supplierId?: number
    periodType?: string
    period?: string
    grade?: string
  }) => get<PageResult<SupplierScoreRow>>('/purchase/supplier-scores', params),
  summary: (periodType?: string, period?: string) =>
    get<SupplierGradeSummary>('/purchase/supplier-scores/summary', { periodType, period }),
  recalc: (periodType?: string, period?: string) => {
    const qs = new URLSearchParams()
    if (periodType) qs.set('periodType', periodType)
    if (period) qs.set('period', period)
    const suffix = qs.size > 0 ? `?${qs.toString()}` : ''
    return post<number>(`/purchase/supplier-scores/recalc${suffix}`)
  },
}
