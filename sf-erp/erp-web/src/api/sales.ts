import { get, post, put, del, type PageResult } from '@/api/request'

/**
 * 销售管理模块 API（订单/出库/退货/统计）。
 * 类型与后端 com.erp.sales.dto.vo / dto.form 一一对应。
 */

// ==================== 通用类型 ====================

/** 销售订单状态：0草稿 10已审核 20部分出库 30已完成 90已关闭 */
export const ORDER_STATUS: Record<number, { label: string; color: string }> = {
  0: { label: '草稿', color: 'default' },
  10: { label: '已审核', color: 'processing' },
  20: { label: '部分出库', color: 'warning' },
  30: { label: '已完成', color: 'success' },
  90: { label: '已关闭', color: 'default' },
}

/** 出库/退货单状态：0草稿 10已审核 */
export const BIZ_STATUS: Record<number, { label: string; color: string }> = {
  0: { label: '草稿', color: 'default' },
  10: { label: '已审核', color: 'success' },
}

/** 基础资料下拉选项（basedata 模块通用行结构，按约定字段读取） */
export interface BaseOption {
  id: number
  code: string
  name: string
}

/** 基础资料下拉加载（跨模块只依赖约定分页结构；未就绪时页面显示空下拉） */
async function fetchOptions(url: string): Promise<BaseOption[]> {
  const page = await get<PageResult<BaseOption>>(url, { pageNum: 1, pageSize: 500 })
  return page.records ?? []
}

export const getCustomerOptions = () => fetchOptions('/basedata/customers')
export const getMaterialOptions = () => fetchOptions('/basedata/materials')
export const getWarehouseOptions = () => fetchOptions('/basedata/warehouses')

// ==================== 销售订单 ====================

/** 销售订单表头行 */
export interface SalOrderRow {
  id: number
  orderNo: string
  customerId: number
  customerCode: string | null
  customerName: string | null
  warehouseId: number
  warehouseName: string | null
  orderDate: string
  totalQty: number
  totalAmount: number
  status: number
  remark: string | null
  auditTime: string | null
  closeTime: string | null
  createTime: string | null
}

/** 销售订单明细行 */
export interface SalOrderDetailRow {
  id: number
  orderId: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  materialSpec: string | null
  qty: number
  price: number
  amount: number
  outQty: number
  remainingQty: number | null
  remark: string | null
}

/** 销售订单详情聚合 */
export interface SalOrderDetail {
  order: SalOrderRow
  details: SalOrderDetailRow[]
}

/** 销售订单提交表单 */
export interface SalOrderFormPayload {
  customerId: number
  warehouseId: number
  orderDate: string
  remark?: string
  details: { materialId: number; qty: number; price: number; remark?: string }[]
}

export function pageOrders(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number
}) {
  return get<PageResult<SalOrderRow>>('/sales/orders', params)
}

export function getOrder(id: number) {
  return get<SalOrderDetail>(`/sales/orders/${id}`)
}

export function createOrder(payload: SalOrderFormPayload) {
  return post<number>('/sales/orders', payload)
}

export function updateOrder(id: number, payload: SalOrderFormPayload) {
  return put<void>(`/sales/orders/${id}`, payload)
}

export function auditOrder(id: number) {
  return put<void>(`/sales/orders/${id}/audit`)
}

export function closeOrder(id: number) {
  return put<void>(`/sales/orders/${id}/close`)
}

export function deleteOrder(id: number) {
  return del<void>(`/sales/orders/${id}`)
}

// ==================== 销售出库单 ====================

/** 销售出库单表头行 */
export interface SalOutstockRow {
  id: number
  outNo: string
  orderId: number
  orderNo: string
  customerId: number
  customerCode: string | null
  customerName: string | null
  warehouseId: number
  warehouseName: string | null
  outDate: string
  totalQty: number
  totalAmount: number
  status: number
  remark: string | null
  auditTime: string | null
  createTime: string | null
}

/** 销售出库单明细行 */
export interface SalOutstockDetailRow {
  id: number
  outId: number
  orderDetailId: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  materialSpec: string | null
  qty: number
  price: number
  amount: number
  returnedQty: number
  returnableQty: number | null
  remark: string | null
}

/** 销售出库单详情聚合 */
export interface SalOutstockDetail {
  outstock: SalOutstockRow
  details: SalOutstockDetailRow[]
}

/** 销售出库单提交表单（从订单选单） */
export interface SalOutstockFormPayload {
  orderId: number
  outDate: string
  remark?: string
  details: { orderDetailId: number; qty: number }[]
}

export function pageOutstock(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number
}) {
  return get<PageResult<SalOutstockRow>>('/sales/outstock', params)
}

export function getOutstock(id: number) {
  return get<SalOutstockDetail>(`/sales/outstock/${id}`)
}

export function createOutstock(payload: SalOutstockFormPayload) {
  return post<number>('/sales/outstock', payload)
}

export function auditOutstock(id: number) {
  return put<void>(`/sales/outstock/${id}/audit`)
}

export function deleteOutstock(id: number) {
  return del<void>(`/sales/outstock/${id}`)
}

// ==================== 销售退货单 ====================

/** 销售退货单表头行 */
export interface SalReturnRow {
  id: number
  returnNo: string
  outId: number
  outNo: string
  customerId: number
  customerCode: string | null
  customerName: string | null
  warehouseId: number
  warehouseName: string | null
  returnDate: string
  totalQty: number
  totalAmount: number
  status: number
  remark: string | null
  auditTime: string | null
  createTime: string | null
}

/** 销售退货单明细行 */
export interface SalReturnDetailRow {
  id: number
  returnId: number
  outDetailId: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  materialSpec: string | null
  qty: number
  price: number
  amount: number
  remark: string | null
}

/** 销售退货单详情聚合 */
export interface SalReturnDetailData {
  ret: SalReturnRow
  details: SalReturnDetailRow[]
}

/** 销售退货单提交表单（引用出库单） */
export interface SalReturnFormPayload {
  outId: number
  returnDate: string
  remark?: string
  details: { outDetailId: number; qty: number }[]
}

export function pageReturns(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number
}) {
  return get<PageResult<SalReturnRow>>('/sales/returns', params)
}

export function getReturn(id: number) {
  return get<SalReturnDetailData>(`/sales/returns/${id}`)
}

export function createReturn(payload: SalReturnFormPayload) {
  return post<number>('/sales/returns', payload)
}

export function auditReturn(id: number) {
  return put<void>(`/sales/returns/${id}/audit`)
}

export function deleteReturn(id: number) {
  return del<void>(`/sales/returns/${id}`)
}

// ==================== 销售统计 ====================

/** 按日统计行 */
export interface SalDailyStat {
  statDate: string
  salesAmount: number
  costAmount: number
  profitAmount: number
  totalQty: number
}

/** 排行行（物料/客户维度） */
export interface SalRankRow {
  refId: number
  refCode: string | null
  refName: string | null
  salesAmount: number
  salesQty: number | null
}

export function getDailyStats(from: string, to: string) {
  return get<SalDailyStat[]>('/sales/statistics/daily', { from, to })
}

export function getSalesRank(type: 'material' | 'customer', from?: string, to?: string) {
  return get<SalRankRow[]>('/sales/statistics/rank', { type, from, to })
}

// ==================== 销售报价单 ====================

/** 报价单状态：0草稿 10已报价 20已转订单 30已过期(后端动态判定) 40已作废 */
export const QUOTATION_STATUS: Record<number, { label: string; semantic: 'default' | 'processing' | 'success' | 'error' }> = {
  0: { label: '草稿', semantic: 'default' },
  10: { label: '已报价', semantic: 'processing' },
  20: { label: '已转订单', semantic: 'success' },
  30: { label: '已过期', semantic: 'error' },
  40: { label: '已作废', semantic: 'default' },
}

/** 报价单表头行（status 为有效状态，已过期的已报价返回 30） */
export interface SalQuotationRow {
  id: number
  quotationNo: string
  customerId: number
  customerCode: string | null
  customerName: string | null
  quoteDate: string
  validUntil: string
  discountRate: number | null
  status: number
  rawStatus: number
  orderId: number | null
  orderNo: string | null
  submitTime: string | null
  voidTime: string | null
  remark: string | null
  createTime: string | null
}

/** 报价单明细行（costPrice 为 ref_cost 快照，只读带回） */
export interface SalQuotationItemRow {
  id: number
  quotationId: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  materialSpec: string | null
  qty: number
  price: number
  costPrice: number | null
  amount: number
  remark: string | null
}

/** 报价单详情聚合 */
export interface SalQuotationDetail {
  quotation: SalQuotationRow
  items: SalQuotationItemRow[]
}

/** 报价单行毛利率视图行 */
export interface QuotationMarginRow {
  itemId: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  materialSpec: string | null
  qty: number
  price: number
  costPrice: number | null
  marginAmount: number | null
  marginRate: number | null
  warn: boolean
  remark: string | null
}

/** 报价单行毛利率视图聚合 */
export interface QuotationMarginView {
  quotationNo: string
  status: number
  warnThreshold: number
  rows: QuotationMarginRow[]
}

/** 报价单提交表单 */
export interface SalQuotationFormPayload {
  customerId: number
  quoteDate: string
  validUntil: string
  discountRate?: number | null
  remark?: string
  items: { materialId: number; qty: number; price: number; remark?: string }[]
}

export function pageQuotations(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  customerId?: number
  status?: number
  validFrom?: string
  validTo?: string
}) {
  return get<PageResult<SalQuotationRow>>('/sales/quotations', params)
}

export function getQuotation(id: number) {
  return get<SalQuotationDetail>(`/sales/quotations/${id}`)
}

export function createQuotation(payload: SalQuotationFormPayload) {
  return post<number>('/sales/quotations', payload)
}

export function updateQuotation(id: number, payload: SalQuotationFormPayload) {
  return put<void>(`/sales/quotations/${id}`, payload)
}

export function deleteQuotation(id: number) {
  return del<void>(`/sales/quotations/${id}`)
}

/** 提交报价：0草稿 → 10已报价 */
export function submitQuotation(id: number) {
  return put<void>(`/sales/quotations/${id}/submit`)
}

/** 作废报价：0/10 → 40已作废 */
export function voidQuotation(id: number) {
  return put<void>(`/sales/quotations/${id}/void`)
}

/** 行毛利率视图（低于警戒线的行 warn=true） */
export function getQuotationMargin(id: number) {
  return get<QuotationMarginView>(`/sales/quotations/${id}/margin`)
}

/** 下推销售订单（返回订单 ID） */
export function quotationToOrder(id: number, payload: { warehouseId?: number; orderDate?: string } = {}) {
  return post<number>(`/sales/quotations/${id}/to-order`, payload)
}

// ==================== 发货通知单 ====================

/** 通知单状态：0草稿 10已通知 20已出库 30已取消 */
export const NOTICE_STATUS: Record<number, { label: string; semantic: 'default' | 'processing' | 'success' }> = {
  0: { label: '草稿', semantic: 'default' },
  10: { label: '已通知', semantic: 'processing' },
  20: { label: '已出库', semantic: 'success' },
  30: { label: '已取消', semantic: 'default' },
}

/** 通知单表头行 */
export interface SalNoticeRow {
  id: number
  noticeNo: string
  orderId: number | null
  orderNo: string | null
  customerId: number
  customerCode: string | null
  customerName: string | null
  warehouseId: number
  warehouseName: string | null
  planDate: string
  status: number
  totalQty: number | null
  totalOutQty: number | null
  notifyTime: string | null
  cancelTime: string | null
  settleTime: string | null
  remark: string | null
  createTime: string | null
}

/** 通知单明细行 */
export interface SalNoticeItemRow {
  id: number
  noticeId: number
  orderDetailId: number | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  materialSpec: string | null
  qty: number
  outQty: number
  remainingQty: number | null
  remark: string | null
}

/** 通知单详情聚合 */
export interface SalNoticeDetail {
  notice: SalNoticeRow
  items: SalNoticeItemRow[]
}

/** 通知单提交表单（从订单生成：orderId+orderDetailId；直发：customerId+warehouseId+materialId） */
export interface SalNoticeFormPayload {
  orderId?: number | null
  customerId?: number | null
  warehouseId?: number | null
  planDate: string
  remark?: string
  details: { orderDetailId?: number; materialId?: number; qty: number; remark?: string }[]
}

/** 备货参考行：物料 × 仓库可用量 */
export interface PendingStockRow {
  materialId: number
  materialCode: string | null
  materialName: string | null
  materialSpec: string | null
  warehouseId: number
  warehouseName: string | null
  availableQty: number
}

export function pageNotices(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number
}) {
  return get<PageResult<SalNoticeRow>>('/sales/delivery-notices', params)
}

export function getNotice(id: number) {
  return get<SalNoticeDetail>(`/sales/delivery-notices/${id}`)
}

export function createNotice(payload: SalNoticeFormPayload) {
  return post<number>('/sales/delivery-notices', payload)
}

export function updateNotice(id: number, payload: SalNoticeFormPayload) {
  return put<void>(`/sales/delivery-notices/${id}`, payload)
}

export function deleteNotice(id: number) {
  return del<void>(`/sales/delivery-notices/${id}`)
}

/** 通知发货：0草稿 → 10已通知 */
export function notifyNotice(id: number) {
  return put<void>(`/sales/delivery-notices/${id}/notify`)
}

/** 取消通知：仅未发生出库时允许 */
export function cancelNotice(id: number) {
  return put<void>(`/sales/delivery-notices/${id}/cancel`)
}

/** 按出库单号核销出库数量（守卫不超通知量） */
export function settleNotice(id: number, outstockNo: string) {
  return post<void>(`/sales/delivery-notices/${id}/settle`, { outstockNo })
}

/** 备货参考：物料 × 仓库可用量 */
export function getPendingStocks(warehouseId?: number, keyword?: string) {
  return get<PendingStockRow[]>('/sales/delivery-notices/pending-stocks', { warehouseId, keyword })
}
