import { get, post, put, del, type PageResult } from '@/api/request'

/**
 * 组织管理模块 API：工厂档案 / 仓库归属 / 调拨价目 / 组织间调拨 / 统计。
 * 类型与后端 com.erp.org 的 VO/Form 对齐；分页响应结构见 api/request.ts。
 */

// ===================== 类型定义（与后端 VO 对齐） =====================

/** 工厂档案行 */
export interface Factory {
  id: number
  code: string
  name: string
  address: string | null
  manager: string | null
  status: number
  remark: string | null
  warehouseCount: number
  createTime: string | null
}

/** 工厂提交负载（code 留空自动生成 FC+日期+流水；修改时 code 不可改） */
export interface FactoryPayload {
  code?: string
  name: string
  address?: string
  manager?: string
  status?: number
  remark?: string
}

/** 仓库归属行（工厂下仓库清单） */
export interface WarehouseBelong {
  id: number
  code: string
  name: string
  manager: string | null
  factoryId: number | null
  factoryName: string | null
  status: number
}

/** 工厂库存统计行（各仓库库存量汇总） */
export interface FactoryStockRow {
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  materialCount: number
  totalQty: number
}

/** 调拨价目行 */
export interface TransferPrice {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  fromFactoryId: number
  fromFactoryName: string | null
  toFactoryId: number
  toFactoryName: string | null
  markupType: 'RATE' | 'FIXED'
  markupTypeText: string
  markupValue: number
  status: number
  remark: string | null
  createTime: string | null
}

/** 价目提交负载 */
export interface TransferPricePayload {
  materialId: number
  fromFactoryId: number
  toFactoryId: number
  markupType: 'RATE' | 'FIXED'
  /** RATE=百分数(10 代表 10%)；FIXED=每单位加价金额 */
  markupValue: number
  status?: number
  remark?: string
}

/** 组织间调拨单列表行 */
export interface TransferOrder {
  id: number
  transferNo: string
  fromFactoryId: number
  fromFactoryName: string | null
  fromWarehouseId: number
  fromWarehouseName: string | null
  toFactoryId: number
  toFactoryName: string | null
  toWarehouseId: number
  toWarehouseName: string | null
  status: number
  statusText: string | null
  auditTime: string | null
  remark: string | null
  createTime: string | null
  lineCount: number
  totalQty: number
  totalAmount: number
}

/** 调拨明细行（详情/价格预演） */
export interface TransferLine {
  id: number | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  qty: number
  /** 成本单价（调出方最近加权价，无成本数据为 0） */
  costPrice: number
  /** 内部结算单价（无价目=成本） */
  settlePrice: number
  settleAmount: number
}

/** 调拨单详情（头 + 明细） */
export interface TransferDetail extends TransferOrder {
  auditBy: number | null
  lines: TransferLine[]
}

/** 调拨单提交负载（成本/结算价由服务端自动计算） */
export interface TransferPayload {
  fromFactoryId: number
  fromWarehouseId: number
  toFactoryId: number
  toWarehouseId: number
  remark?: string
  lines: { materialId: number; qty: number }[]
}

/** 调拨线路统计行（期间内已审核调拨汇总） */
export interface TransferLineStat {
  fromFactoryId: number
  fromFactoryName: string | null
  toFactoryId: number
  toFactoryName: string | null
  transferCount: number
  totalQty: number
  totalAmount: number
}

// ===================== 查询参数 =====================

export interface FactoryQueryParams {
  pageNum?: number
  pageSize?: number
  name?: string
  status?: number
}

export interface TransferPriceQueryParams {
  pageNum?: number
  pageSize?: number
  materialId?: number
  fromFactoryId?: number
  toFactoryId?: number
  status?: number
}

export interface TransferQueryParams {
  pageNum?: number
  pageSize?: number
  status?: number
  fromFactoryId?: number
  toFactoryId?: number
  keyword?: string
  beginDate?: string
  endDate?: string
}

// ===================== 工厂档案 / 仓库归属 =====================

export function pageFactories(params: FactoryQueryParams) {
  return get<PageResult<Factory>>('/org/factories', params as Record<string, unknown>)
}

export function listEnabledFactories() {
  return get<Factory[]>('/org/factories/enabled')
}

export function getFactory(id: number) {
  return get<Factory>(`/org/factories/${id}`)
}

export function createFactory(data: FactoryPayload) {
  return post<{ id: number }>('/org/factories', data)
}

export function updateFactory(id: number, data: FactoryPayload) {
  return put<void>(`/org/factories/${id}`, data)
}

export function deleteFactory(id: number) {
  return del<void>(`/org/factories/${id}`)
}

export function updateFactoryStatus(id: number, status: number) {
  return put<void>(`/org/factories/${id}/status`, { status })
}

export function listFactoryWarehouses(factoryId: number) {
  return get<WarehouseBelong[]>(`/org/factories/${factoryId}/warehouses`)
}

export function assignWarehouse(warehouseId: number, factoryId: number) {
  return put<void>('/org/factories/assign-warehouse', { warehouseId, factoryId })
}

// ===================== 调拨价目 =====================

export function pageTransferPrices(params: TransferPriceQueryParams) {
  return get<PageResult<TransferPrice>>('/org/transfer-prices', params as Record<string, unknown>)
}

export function createTransferPrice(data: TransferPricePayload) {
  return post<{ id: number }>('/org/transfer-prices', data)
}

export function updateTransferPrice(id: number, data: TransferPricePayload) {
  return put<void>(`/org/transfer-prices/${id}`, data)
}

export function deleteTransferPrice(id: number) {
  return del<void>(`/org/transfer-prices/${id}`)
}

export function updateTransferPriceStatus(id: number, status: number) {
  return put<void>(`/org/transfer-prices/${id}/status`, { status })
}

// ===================== 组织间调拨单 =====================

export function pageTransfers(params: TransferQueryParams) {
  return get<PageResult<TransferOrder>>('/org/transfers', params as Record<string, unknown>)
}

export function getTransfer(id: number) {
  return get<TransferDetail>(`/org/transfers/${id}`)
}

/** 明细价格预演：提交前实时展示成本/结算价列（不落库） */
export function previewTransfer(data: TransferPayload) {
  return post<TransferLine[]>('/org/transfers/preview', data)
}

export function createTransfer(data: TransferPayload) {
  return post<{ id: number }>('/org/transfers', data)
}

export function updateTransfer(id: number, data: TransferPayload) {
  return put<void>(`/org/transfers/${id}`, data)
}

export function auditTransfer(id: number) {
  return post<void>(`/org/transfers/${id}/audit`)
}

export function cancelTransfer(id: number) {
  return post<void>(`/org/transfers/${id}/cancel`)
}

// ===================== 统计 =====================

export function factoryStockStats(factoryId: number) {
  return get<FactoryStockRow[]>('/org/statistics/stock', { factoryId })
}

export function transferLineStats(from: string, to: string) {
  return get<TransferLineStat[]>('/org/statistics/transfer', { from, to })
}

// ===================== 基础资料下拉（跨模块只读约定） =====================

export interface WarehouseOption {
  id: number
  code: string
  name: string
}

export interface MaterialOption {
  id: number
  code: string
  name: string
}

export function fetchWarehouseOptions() {
  return get<WarehouseOption[]>('/basedata/warehouses/enabled')
}

export function fetchMaterialOptions() {
  return get<MaterialOption[]>('/basedata/materials/enabled')
}

// ===================== 格式化 =====================

/** 金额格式化：2 位小数 + 千分位 */
export function fmtMoney(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 单价格式化：最多 6 位小数去尾零 */
export function fmtPrice(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 6 })
}

/** 数量格式化：最多 4 位小数去尾零 */
export function fmtQty(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}
