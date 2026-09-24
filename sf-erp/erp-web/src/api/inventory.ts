import { get, post, put, del, type PageResult } from '@/api/request'

/**
 * 库存管理模块 API：即时库存/预警/流水/调拨/其他出入库/盘点。
 * 类型与后端 com.erp.inventory 的 VO/Form 对齐；分页响应结构见 api/request.ts。
 */

// ===================== 类型定义（与后端 VO 对齐） =====================

/** 即时库存行 */
export interface StockRow {
  id: number
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  batchNo: string
  /** 库位 ID（0=汇总/未上架行，>0=库位行，V421） */
  locationId: number
  /** 库位编码（locationId=0 时为 null） */
  locationCode: string | null
  locationName: string | null
  qty: number
  safetyStock: number | null
  belowSafety: boolean
  updateTime: string | null
}

/** 安全库存预警行 */
export interface WarningRow {
  stockId: number | null
  warehouseId: number | null
  warehouseCode: string | null
  warehouseName: string | null
  materialId: number
  materialCode: string
  materialName: string
  qty: number
  safetyStock: number
  shortage: number
}

/** 库存流水行 */
export interface MovementRow {
  id: number
  bizType: string
  bizId: number | null
  bizNo: string | null
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  qty: number
  remark: string | null
  createTime: string
}

/** 其他出入库单列表行 */
export interface OtherDocRow {
  id: number
  no: string
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  reason: string | null
  status: number
  auditTime: string | null
  remark: string | null
  lineCount: number
  totalQty: number
  createTime: string
}

/** 其他出入库单明细行 */
export interface OtherDocLine {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  /** 批次号（非批次物料为空串；出库单为空=FIFO 自动分配） */
  batchNo: string | null
  qty: number
  remark: string | null
}

/** 其他出入库单详情（头 + 行） */
export interface OtherDocDetail {
  id: number
  no: string
  warehouseId: number
  reason: string | null
  status: number
  auditTime: string | null
  remark: string | null
  createTime: string
  lines: OtherDocLine[]
}

/** 盘点单列表行 */
export interface CheckRow {
  id: number
  no: string
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  status: number
  completeTime: string | null
  remark: string | null
  lineCount: number
  actualCount: number
  diffCount: number
  createTime: string
}

/** 盘点明细行（快照 + 实盘 + 差异） */
export interface CheckDetailLine {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  /** 批次号（快照自库存行；非批次物料为空串） */
  batchNo: string | null
  bookQty: number
  actualQty: number | null
  diffQty: number | null
}

/** 盘点单详情（头 + 行） */
export interface CheckDocDetail {
  id: number
  no: string
  warehouseId: number
  status: number
  completeTime: string | null
  remark: string | null
  createTime: string
  lines: CheckDetailLine[]
}

// ===================== 查询参数 / 提交负载 =====================

/** 即时库存查询参数 */
export interface StockQueryParams {
  pageNum?: number
  pageSize?: number
  warehouseId?: number
  keyword?: string
  /** all=全部 hide=隐藏零库存 only=仅零库存 */
  zero?: 'all' | 'hide' | 'only'
  warningOnly?: boolean
}

/** 流水查询参数 */
export interface MovementQueryParams {
  pageNum?: number
  pageSize?: number
  bizType?: string
  beginDate?: string
  endDate?: string
  materialId?: number
  warehouseId?: number
  keyword?: string
}

/** 其他出入库单查询参数 */
export interface OtherDocQueryParams {
  pageNum?: number
  pageSize?: number
  status?: number
  warehouseId?: number
  keyword?: string
}

/** 盘点单查询参数 */
export interface CheckQueryParams {
  pageNum?: number
  pageSize?: number
  status?: number
  warehouseId?: number
  keyword?: string
}

/** 其他出入库单提交负载 */
export interface OtherDocPayload {
  warehouseId: number
  reason?: string
  remark?: string
  /** 出库策略（V422，仅出库单生效）：FIFO 默认 / FEFO 先到期先出 / AVG 总量扣减 */
  strategy?: OutStrategy
  /** batchNo：入库行批次物料必填（审核时校验）；出库行传值=指定批次(BATCH)，空=策略自动分配 */
  lines: {
    materialId: number
    batchNo?: string
    /** 效期（V422，仅入库行生效）：批次行写入 expiry_date，FEFO 出库依据 */
    expiryDate?: string
    qty: number
    remark?: string
  }[]
}

/** 调拨提交负载 */
export interface TransferPayload {
  fromWarehouseId: number
  toWarehouseId: number
  remark?: string
  lines: { materialId: number; qty: number }[]
}

/** 基础资料下拉选项（跨模块只读约定，字段最小集） */
export interface WarehouseOption {
  id: number
  code: string
  name: string
}

export interface MaterialOption {
  id: number
  code: string
  name: string
  safetyStock?: number
  /** 批次管理标志：1=批次物料（出入库需带批次号）0=普通物料（/materials/enabled 返回全量字段） */
  batchManaged?: number
  /** 条码（移动端扫码匹配用） */
  barcode?: string | null
}

// ===================== 业务类型映射 =====================

/** 流水业务类型 -> 中文（与后端各模块写入 inv_movement 的 biz_type 对齐） */
export const BIZ_TYPE_MAP: Record<string, string> = {
  PUR_IN: '采购入库',
  PUR_RET: '采购退货',
  SAL_OUT: '销售出库',
  SAL_RET: '销售退货',
  OTH_IN: '其他入库',
  OTH_OUT: '其他出库',
  ADJUST: '盘点调整',
  MOVE: '仓库调拨',
  PROD_IN: '生产入库',
  PROD_USE: '生产领料',
  // 二期补充：设备备件 与 组织间调拨
  EQP_IN: '备件入库',
  EQP_OUT: '备件出库',
  ORG_IN: '组织调入',
  ORG_OUT: '组织调出',
}

/** 数量格式化：去掉多余 0，千分位 */
export function fmtQty(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

// ===================== API =====================

/** 即时库存分页 */
export function fetchStockPage(params: StockQueryParams) {
  return get<PageResult<StockRow>>('/inventory/stocks', params as Record<string, unknown>)
}

/** 安全库存预警分页 */
export function fetchWarningPage(params: StockQueryParams) {
  return get<PageResult<WarningRow>>('/inventory/warnings', params as Record<string, unknown>)
}

/** 库存流水分页 */
export function fetchMovementPage(params: MovementQueryParams) {
  return get<PageResult<MovementRow>>('/inventory/movements', params as Record<string, unknown>)
}

/** 执行仓库调拨（一出一入，返回调拨单号） */
export function submitTransfer(data: TransferPayload) {
  return post<{ transferNo: string }>('/inventory/transfers', data)
}

// ---------- 其他入库 ----------

export function fetchOtherInPage(params: OtherDocQueryParams) {
  return get<PageResult<OtherDocRow>>('/inventory/other-ins', params as Record<string, unknown>)
}

export function fetchOtherInDetail(id: number) {
  return get<OtherDocDetail>(`/inventory/other-ins/${id}`)
}

export function createOtherIn(data: OtherDocPayload) {
  return post<{ id: number }>('/inventory/other-ins', data)
}

export function auditOtherIn(id: number) {
  return post<void>(`/inventory/other-ins/${id}/audit`)
}

export function deleteOtherIn(id: number) {
  return del<void>(`/inventory/other-ins/${id}`)
}

// ---------- 其他出库 ----------

export function fetchOtherOutPage(params: OtherDocQueryParams) {
  return get<PageResult<OtherDocRow>>('/inventory/other-outs', params as Record<string, unknown>)
}

export function fetchOtherOutDetail(id: number) {
  return get<OtherDocDetail>(`/inventory/other-outs/${id}`)
}

export function createOtherOut(data: OtherDocPayload) {
  return post<{ id: number }>('/inventory/other-outs', data)
}

export function auditOtherOut(id: number) {
  return post<void>(`/inventory/other-outs/${id}/audit`)
}

export function deleteOtherOut(id: number) {
  return del<void>(`/inventory/other-outs/${id}`)
}

// ---------- 库存盘点 ----------

export function fetchCheckPage(params: CheckQueryParams) {
  return get<PageResult<CheckRow>>('/inventory/checks', params as Record<string, unknown>)
}

export function fetchCheckDetail(id: number) {
  return get<CheckDocDetail>(`/inventory/checks/${id}`)
}

export function createCheck(data: { warehouseId: number; remark?: string }) {
  return post<{ id: number }>('/inventory/checks', data)
}

export function saveCheckActual(
  id: number,
  lines: { detailId: number; actualQty: number }[],
) {
  return put<void>(`/inventory/checks/${id}`, { lines })
}

export function completeCheck(id: number) {
  return post<{ adjusted: number }>(`/inventory/checks/${id}/complete`)
}

export function deleteCheck(id: number) {
  return del<void>(`/inventory/checks/${id}`)
}

// ---------- 基础资料下拉（只读跨模块接口，字段最小集） ----------

/** 启用仓库下拉 */
export function fetchWarehouseOptions() {
  return get<WarehouseOption[]>('/basedata/warehouses/enabled')
}

/** 启用物料下拉（可带关键词） */
export function fetchMaterialOptions(keyword?: string) {
  return get<MaterialOption[]>('/basedata/materials/enabled', keyword ? { keyword } : undefined)
}

// ===================== 二期：批次管理与追溯（V411） =====================

/** 批次余额行（批次清单 / 移动端拣货建议；V421 起按批次聚合未上架+库位行） */
export interface BatchRow {
  id: number
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  batchNo: string
  qty: number
  firstInAt: string | null
  /** 批次效期（批次内最早；可空，V422） */
  expiryDate: string | null
  /** 库龄（天）= 今天 - 首次入库日期 */
  ageDays: number | null
}

/** 批次追溯节点（正溯=去向流水 / 反溯=来源流水） */
export interface TraceNode {
  id: number
  bizType: string
  bizId: number | null
  bizNo: string | null
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  qty: number
  remark: string | null
  createTime: string
}

/** 出库策略（与后端 com.erp.inventory.constant.OutStrategy 对齐；V422 起 FEFO） */
export type OutStrategy = 'FIFO' | 'BATCH' | 'AVG' | 'FEFO'

/** 批次余额清单（按首入时间升序 = FIFO 建议顺序；只含 batch_no != '' 的批次行） */
export function fetchBatches(materialId: number, warehouseId?: number) {
  return get<BatchRow[]>(
    '/inventory/batches',
    warehouseId != null ? { materialId, warehouseId } : { materialId },
  )
}

/** 批次正溯：该批次出库流向（出库单/调拨/领用单号） */
export function fetchForwardTrace(materialId: number, batchNo: string) {
  return get<TraceNode[]>('/inventory/trace/forward', { materialId, batchNo })
}

/** 批次反溯：该批次的入库来源（入库单/采购单号） */
export function fetchBackwardTrace(materialId: number, batchNo: string) {
  return get<TraceNode[]>('/inventory/trace/backward', { materialId, batchNo })
}

// ===================== 三期：WMS 库内作业（V421/V422） =====================

/** WMS 作业任务行 */
export interface WmsTaskRow {
  id: number
  taskNo: string
  taskType: 'PUTAWAY' | 'PICK' | 'MOVE' | 'REPLENISH'
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  batchNo: string
  qty: number
  /** 目标/来源库位（PUTAWAY/REPLENISH=目标，PICK=来源；null=未定位） */
  locationId: number | null
  locationCode: string | null
  fromLocationId: number | null
  fromLocationCode: string | null
  waveId: number | null
  waveNo: string | null
  sourceType: string
  sourceNo: string | null
  assignee: number | null
  assigneeName: string | null
  /** 0待分配 10待执行 20执行中 30完成 40取消 */
  status: number
  recommendReason: string | null
  finishTime: string | null
  remark: string | null
  createTime: string
}

/** 任务类型中文映射 */
export const TASK_TYPE_MAP: Record<string, string> = {
  PUTAWAY: '上架',
  PICK: '拣选',
  MOVE: '移库',
  REPLENISH: '补货',
}

/** 任务状态中文映射 */
export const TASK_STATUS_MAP: Record<number, string> = {
  0: '待分配',
  10: '待执行',
  20: '执行中',
  30: '已完成',
  40: '已取消',
}

/** 任务查询参数 */
export interface WmsTaskQueryParams {
  pageNum?: number
  pageSize?: number
  taskType?: string
  status?: number
  warehouseId?: number
  assignee?: number
  keyword?: string
}

/** 库内 KPI */
export interface WmsKpi {
  completedTasks: number
  avgDurationMinutes: number
  onTimeRate: number
  stockAccuracy: number | null
  statusCounts: { status: number; cnt: number }[]
  byAssignee: { assignee: number; assigneeName: string | null; completed: number; avgDurationMinutes: number }[]
}

/** 波次行 */
export interface WmsWaveRow {
  id: number
  waveNo: string
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  /** 0建波 10拣选中 20完成 */
  status: number
  taskCount: number
  doneCount: number
  totalQty: number
  finishTime: string | null
  remark: string | null
  createTime: string
}

/** 建波可选出库单 */
export interface OutboundDocRow {
  docId: number
  sourceType: 'SAL_OUT' | 'OTH_OUT'
  docNo: string
  warehouseId: number
  lineCount: number
  totalQty: number
  auditTime: string | null
}

/** ABC 分级行 */
export interface AbcClassRow {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  level: 'A' | 'B' | 'C'
  freq: number
  calcDate: string
}

/** 序列号台账行 */
export interface SerialRow {
  id: number
  serialNo: string
  materialId: number
  materialCode: string | null
  materialName: string | null
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  batchNo: string
  status: 'IN_STOCK' | 'OUT' | 'SCRAP'
  sourceNo: string | null
  outNo: string | null
  remark: string | null
  createTime: string
  updateTime: string
}

/** 序列号状态中文映射 */
export const SERIAL_STATUS_MAP: Record<string, string> = {
  IN_STOCK: '在库',
  OUT: '已出库',
  SCRAP: '报废',
}

// ---------- WMS 作业任务 ----------

export function fetchWmsTaskPage(params: WmsTaskQueryParams) {
  return get<PageResult<WmsTaskRow>>('/inventory/wms/tasks', params as Record<string, unknown>)
}

export function fetchWmsTaskBoard(warehouseId?: number) {
  return get<{ task_type: string; status: number; cnt: number }[]>(
    '/inventory/wms/tasks/board',
    warehouseId != null ? { warehouseId } : undefined,
  )
}

export function fetchWmsKpi(from?: string, to?: string) {
  return get<WmsKpi>(
    '/inventory/wms/kpi',
    from || to ? { from, to } : undefined,
  )
}

export function claimWmsTask(id: number) {
  return post<void>(`/inventory/wms/tasks/${id}/claim`)
}

export function startWmsTask(id: number) {
  return post<void>(`/inventory/wms/tasks/${id}/start`)
}

export function completeWmsTask(id: number, data?: { actualQty?: number; locationId?: number }) {
  return post<void>(`/inventory/wms/tasks/${id}/complete`, data ?? {})
}

export function cancelWmsTask(id: number) {
  return del<void>(`/inventory/wms/tasks/${id}`)
}

export function createWmsManualTask(data: {
  taskType: 'MOVE' | 'REPLENISH' | 'PICK'
  warehouseId: number
  materialId: number
  batchNo?: string
  qty: number
  fromLocationId?: number
  locationId?: number
  remark?: string
}) {
  return post<{ id: number }>('/inventory/wms/tasks', data)
}

export function triggerWmsScan() {
  return post<{ created: number }>('/inventory/wms/tasks/scan')
}

// ---------- WMS 波次 ----------

export function fetchWmsWavePage(params: WmsTaskQueryParams) {
  return get<PageResult<WmsWaveRow>>('/inventory/wms/waves', params as Record<string, unknown>)
}

export function fetchWmsWaveDetail(id: number) {
  return get<WmsWaveRow>(`/inventory/wms/waves/${id}`)
}

export function fetchWavePickPath(id: number) {
  return get<WmsTaskRow[]>(`/inventory/wms/waves/${id}/pick-path`)
}

export function fetchOutboundDocsForWave(warehouseId?: number) {
  return get<OutboundDocRow[]>(
    '/inventory/wms/waves/outbound-docs',
    warehouseId != null ? { warehouseId } : undefined,
  )
}

export function createWmsWave(data: {
  warehouseId: number
  remark?: string
  docs: { docId: number; sourceType: string }[]
}) {
  return post<{ id: number }>('/inventory/wms/waves', data)
}

export function completeWmsWave(id: number) {
  return post<void>(`/inventory/wms/waves/${id}/complete`)
}

// ---------- WMS ABC 分级 ----------

export function fetchAbcPage(params: { pageNum?: number; pageSize?: number; level?: string; keyword?: string }) {
  return get<PageResult<AbcClassRow>>('/inventory/wms/abc', params as Record<string, unknown>)
}

export function fetchAbcCounts() {
  return get<{ level: string; cnt: number }[]>('/inventory/wms/abc/counts')
}

export function recalcAbc() {
  return post<{ level: string; cnt: number }[]>('/inventory/wms/abc/recalc')
}

export function triggerCycleChecks() {
  return post<{ created: number }>('/inventory/wms/abc/cycle-checks')
}

// ---------- 序列号 ----------

export function fetchSerialPage(params: {
  pageNum?: number
  pageSize?: number
  serialNo?: string
  materialId?: number
  status?: string
  docNo?: string
}) {
  return get<PageResult<SerialRow>>('/inventory/serials', params as Record<string, unknown>)
}

export function registerSerials(data: {
  materialId: number
  warehouseId: number
  batchNo?: string
  sourceNo?: string
  serialNos: string[]
}) {
  return post<{ registered: number }>('/inventory/serials', data)
}

export function writeOffSerials(data: { outNo?: string; serialNos: string[] }) {
  return post<{ writtenOff: number }>('/inventory/serials/write-off', data)
}

export function scrapSerials(data: { serialNos: string[]; remark?: string }) {
  return post<{ scrapped: number }>('/inventory/serials/scrap', data)
}
