import { get, post, put, del, type PageResult } from '@/api/request'

/**
 * 设备管理模块 API：设备台账 / 保养计划 / 维修工单 / 统计。
 * 类型与后端 com.erp.equipment 的 VO/Form 对齐；分页响应结构见 api/request.ts。
 */

// ===================== 类型定义（与后端 VO 对齐） =====================

/** 设备台账行 */
export interface DeviceRow {
  id: number
  code: string
  name: string
  model: string | null
  manufacturer: string | null
  purchaseDate: string | null
  originalValue: number
  workshop: string | null
  status: number
  statusLabel: string
  responsiblePerson: string | null
  checkPeriodDays: number | null
  remark: string | null
  createTime: string
}

/** 保养计划行 */
export interface PlanRow {
  id: number
  deviceId: number
  deviceCode: string | null
  deviceName: string | null
  maintainType: number
  maintainTypeLabel: string
  cycleDays: number
  lastDate: string | null
  nextDate: string | null
  /** 距到期天数（负数=已逾期 |天数| 天，0=今天到期；无下次日期为 null） */
  daysLeft: number | null
  overdue: boolean
  remark: string | null
}

/** 维修工单行 */
export interface RepairRow {
  id: number
  repairNo: string
  deviceId: number
  deviceCode: string | null
  deviceName: string | null
  faultDesc: string
  reporter: string | null
  reportTime: string
  status: number
  statusLabel: string
  repairer: string | null
  faultReason: string | null
  finishTime: string | null
  spareCost: number
  remark: string | null
  createTime: string
}

/** 工单备件明细行 */
export interface RepairItemRow {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  qty: number
  costRef: number
}

/** 维修工单详情（头 + 行） */
export interface RepairDetail extends Omit<RepairRow, 'spareCost'> {
  warehouseId: number | null
  lines: RepairItemRow[]
}

/** 设备状态分布项 */
export interface StatusCount {
  status: number
  statusLabel: string
  count: number
}

/** 当月维修次数 TOP 设备项 */
export interface DeviceRepairTop {
  deviceId: number
  deviceCode: string
  deviceName: string
  repairCount: number
}

/** 统计汇总 */
export interface EquipmentStatistics {
  statusDist: StatusCount[]
  monthRepairTop: DeviceRepairTop[]
  monthSpareCost: number
}

// ===================== 查询参数 / 提交负载 =====================

export interface DeviceQueryParams {
  pageNum?: number
  pageSize?: number
  status?: number
  keyword?: string
}

export interface PlanQueryParams {
  pageNum?: number
  pageSize?: number
  deviceId?: number
  dueOnly?: boolean
}

export interface RepairQueryParams {
  pageNum?: number
  pageSize?: number
  status?: number
  deviceId?: number
  keyword?: string
}

export interface DevicePayload {
  code?: string
  name: string
  model?: string
  manufacturer?: string
  purchaseDate?: string
  originalValue: number
  workshop?: string
  status: number
  responsiblePerson?: string
  checkPeriodDays?: number
  remark?: string
}

export interface PlanPayload {
  deviceId: number
  maintainType: number
  cycleDays: number
  lastDate?: string
  remark?: string
}

export interface RepairReportPayload {
  deviceId: number
  faultDesc: string
  reporter?: string
  remark?: string
}

export interface RepairCompletePayload {
  faultReason?: string
  warehouseId?: number
  lines: { materialId: number; qty: number }[]
}

// ===================== 枚举映射 =====================

/** 设备状态 -> Tag 颜色 */
export const DEVICE_STATUS_COLOR: Record<number, string> = {
  1: 'success',
  2: 'warning',
  3: 'processing',
  4: 'default',
}

/** 设备状态选项（列表筛选/状态流转共用） */
export const DEVICE_STATUS_OPTIONS = [
  { label: '运行', value: 1 },
  { label: '停机', value: 2 },
  { label: '维修中', value: 3 },
  { label: '报废', value: 4 },
]

/** 工单状态 -> Tag 颜色 */
export const REPAIR_STATUS_COLOR: Record<number, string> = {
  0: 'error',
  1: 'processing',
  2: 'success',
  3: 'default',
}

/** 工单状态选项 */
export const REPAIR_STATUS_OPTIONS = [
  { label: '待维修', value: 0 },
  { label: '维修中', value: 1 },
  { label: '已修复', value: 2 },
  { label: '已关闭', value: 3 },
]

/** 保养类型选项 */
export const MAINTAIN_TYPE_OPTIONS = [
  { label: '一级保养', value: 1 },
  { label: '二级保养', value: 2 },
  { label: '大修', value: 3 },
]

/** 金额格式化：千分位 + 2 位小数 */
export function fmtMoney(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 数量格式化：去掉多余 0 */
export function fmtQty(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

// ===================== API =====================

// ---------- 设备台账 ----------

export function fetchDevicePage(params: DeviceQueryParams) {
  return get<PageResult<DeviceRow>>('/equipment/devices', params as Record<string, unknown>)
}

export function fetchDeviceDetail(id: number) {
  return get<DeviceRow>(`/equipment/devices/${id}`)
}

export function createDevice(data: DevicePayload) {
  return post<{ id: number }>('/equipment/devices', data)
}

export function updateDevice(id: number, data: DevicePayload) {
  return put<void>(`/equipment/devices/${id}`, data)
}

export function deleteDevice(id: number) {
  return del<void>(`/equipment/devices/${id}`)
}

/** 设备状态流转（报废单向终态） */
export function changeDeviceStatus(id: number, status: number) {
  return put<void>(`/equipment/devices/${id}/status`, { status })
}

// ---------- 保养计划 ----------

export function fetchPlanPage(params: PlanQueryParams) {
  return get<PageResult<PlanRow>>('/equipment/plans', params as Record<string, unknown>)
}

/** 到期清单（已到期 + days 天内到期，默认 7） */
export function fetchDuePlans(days = 7) {
  return get<PlanRow[]>('/equipment/plans/due', { days })
}

export function createPlan(data: PlanPayload) {
  return post<{ id: number }>('/equipment/plans', data)
}

export function updatePlan(id: number, data: PlanPayload) {
  return put<void>(`/equipment/plans/${id}`, data)
}

export function deletePlan(id: number) {
  return del<void>(`/equipment/plans/${id}`)
}

/** 执行一次保养（计划顺延一个周期） */
export function executePlan(id: number, data?: { executor?: string; content?: string }) {
  return post<void>(`/equipment/plans/${id}/execute`, data ?? {})
}

// ---------- 维修工单 ----------

export function fetchRepairPage(params: RepairQueryParams) {
  return get<PageResult<RepairRow>>('/equipment/repairs', params as Record<string, unknown>)
}

export function fetchRepairDetail(id: number) {
  return get<RepairDetail>(`/equipment/repairs/${id}`)
}

/** 报修（创建待维修工单） */
export function reportRepair(data: RepairReportPayload) {
  return post<{ id: number }>('/equipment/repairs', data)
}

/** 接单（待维修 → 维修中） */
export function acceptRepair(id: number, repairer: string) {
  return put<void>(`/equipment/repairs/${id}/accept`, { repairer })
}

/** 完工（备件出库，库存不足整体回滚） */
export function completeRepair(id: number, data: RepairCompletePayload) {
  return put<void>(`/equipment/repairs/${id}/complete`, data)
}

/** 关闭工单（已修复 → 已关闭） */
export function closeRepair(id: number) {
  return put<void>(`/equipment/repairs/${id}/close`)
}

// ---------- 统计 ----------

export function fetchStatistics() {
  return get<EquipmentStatistics>('/equipment/statistics')
}

// ---------- 跨模块只读下拉（basedata 公共接口，字段最小集） ----------

/** 启用仓库下拉（完工备件出库仓库选择） */
export function fetchWarehouseOptions() {
  return get<{ id: number; code: string; name: string }[]>('/basedata/warehouses/enabled')
}

/** 启用物料下拉（备件行选择） */
export function fetchMaterialOptions(keyword?: string) {
  return get<{ id: number; code: string; name: string }[]>(
    '/basedata/materials/enabled',
    keyword ? { keyword } : undefined,
  )
}

/** 设备下拉（分页接口取一页 500 条，用于筛选与计划表单） */
export async function fetchDeviceOptions(): Promise<{ id: number; code: string; name: string }[]> {
  const page = await fetchDevicePage({ pageNum: 1, pageSize: 500 })
  return page.records.map((d) => ({ id: d.id, code: d.code, name: d.name }))
}
