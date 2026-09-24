import { get, post, put, del, type PageResult } from './request'

/**
 * 质量管理模块 API 定义（检验模板 / 检验单 / 不合格品处置 / 质量统计）。
 * 出参类型与后端 VO 严格对齐；数量后端 DECIMAL，JSON 序列化为 number。
 */

/** 检验类型 */
export type InspectType = 'IQC' | 'IPQC' | 'FQC' | 'OQC'

/** 检验类型选项（与后端 qc_template.inspect_type 注释一致） */
export const INSPECT_TYPE_OPTIONS: { value: InspectType; label: string }[] = [
  { value: 'IQC', label: 'IQC 来料检验' },
  { value: 'IPQC', label: 'IPQC 过程检验' },
  { value: 'FQC', label: 'FQC 成品检验' },
  { value: 'OQC', label: 'OQC 出货检验' },
]

/** 检验单状态：0草稿 10已检验 */
export type InspectStatus = 0 | 10

/** 检验结论：1合格 2让步接收 3退货 */
export type InspectConclusion = 1 | 2 | 3

/** 来源单据类型 */
export type QcBizType = 'pur_instock' | 'mrp_work_order' | 'sal_return'

/** 来源单据类型选项 */
export const BIZ_TYPE_OPTIONS: { value: QcBizType; label: string }[] = [
  { value: 'pur_instock', label: '采购入库单' },
  { value: 'mrp_work_order', label: '生产工单' },
  { value: 'sal_return', label: '销售退货单' },
]

/** 处置决定：1退货 2返工 3让步接收 4报废 */
export type RejectDecision = 1 | 2 | 3 | 4

/* ==================== 检验模板 ==================== */

export interface QcTemplateItemRow {
  id: number
  itemName: string
  standardValue: string | null
  lowerLimit: number | null
  upperLimit: number | null
  inspectMethod: string | null
  sampleRate: number
  seq: number
  remark: string | null
}

export interface QcTemplateRow {
  id: number
  templateCode: string
  templateName: string
  categoryId: number | null
  categoryName: string | null
  inspectType: InspectType
  status: 0 | 1
  remark: string | null
  createTime: string
}

export interface QcTemplateDetail extends QcTemplateRow {
  items: QcTemplateItemRow[]
}

/** 模板检验项目提交行 */
export interface QcTemplateItemInput {
  itemName: string
  standardValue?: string
  lowerLimit?: number
  upperLimit?: number
  inspectMethod?: string
  sampleRate: number
  seq?: number
  remark?: string
}

/** 模板新增/修改提交体 */
export interface QcTemplateSavePayload {
  templateCode: string
  templateName: string
  categoryId?: number
  inspectType: InspectType
  status: 0 | 1
  remark?: string
  items: QcTemplateItemInput[]
}

export interface QcTemplatePageParams {
  pageNum: number
  pageSize: number
  keyword?: string
  inspectType?: InspectType
  status?: 0 | 1
}

export const templateApi = {
  page: (params: QcTemplatePageParams) => get<PageResult<QcTemplateRow>>('/qc/templates', { ...params }),
  get: (id: number) => get<QcTemplateDetail>(`/qc/templates/${id}`),
  create: (payload: QcTemplateSavePayload) => post<number>('/qc/templates', payload),
  update: (id: number, payload: QcTemplateSavePayload) => put<void>(`/qc/templates/${id}`, payload),
  remove: (id: number) => del<void>(`/qc/templates/${id}`),
}

/* ==================== 检验单 ==================== */

export interface QcInspectItemRow {
  id: number
  itemName: string
  standardValue: string | null
  lowerLimit: number | null
  upperLimit: number | null
  inspectMethod: string | null
  sampleRate: number
  actualValue: string | null
  lineResult: 0 | 1 | null
  seq: number
  remark: string | null
}

export interface QcInspectRow {
  id: number
  inspectNo: string
  inspectType: InspectType
  sourceType: QcBizType | null
  sourceId: number | null
  sourceNo: string | null
  templateId: number | null
  templateName: string | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  spec: string | null
  unit: string | null
  supplierId: number | null
  supplierName: string | null
  sampleQty: number
  qualifiedQty: number
  unqualifiedQty: number
  conclusion: InspectConclusion | null
  inspectorName: string | null
  inspectDate: string | null
  status: InspectStatus
  submitTime: string | null
  remark: string | null
  createTime: string
}

export interface QcInspectDetail extends QcInspectRow {
  items: QcInspectItemRow[]
}

export interface QcInspectPageParams {
  pageNum: number
  pageSize: number
  keyword?: string
  inspectType?: InspectType
  sourceType?: QcBizType
  materialId?: number
  conclusion?: InspectConclusion
  status?: InspectStatus
  dateBegin?: string
  dateEnd?: string
}

/** 手工新增检验单提交体（明细不传则后端按模板自动带出） */
export interface QcInspectSavePayload {
  inspectType: InspectType
  sourceType?: QcBizType
  sourceId?: number
  sourceNo?: string
  materialId: number
  supplierId?: number
  sampleQty: number
  remark?: string
}

/** 提交检验（实检值 + 结论） */
export interface QcInspectSubmitPayload {
  inspectorName: string
  inspectDate: string
  conclusion: InspectConclusion
  qualifiedQty: number
  unqualifiedQty: number
  remark?: string
  items: { id: number; actualValue?: string; lineResult?: 0 | 1 | null }[]
}

/** 来源单未完成检验卡点结果 */
export interface InspectPendingResult {
  bizType: QcBizType
  bizId: number
  pending: boolean
  pendingCount: number
  pendingList: { id: number; inspectNo: string; status: InspectStatus }[] | null
}

export const inspectApi = {
  page: (params: QcInspectPageParams) => get<PageResult<QcInspectRow>>('/qc/inspects', { ...params }),
  get: (id: number) => get<QcInspectDetail>(`/qc/inspects/${id}`),
  create: (payload: QcInspectSavePayload) => post<number>('/qc/inspects', payload),
  submit: (id: number, payload: QcInspectSubmitPayload) => put<void>(`/qc/inspects/${id}/submit`, payload),
  remove: (id: number) => del<void>(`/qc/inspects/${id}`),
  /** 挂点：按来源单自动生成检验单（返回新生成的检验单 ID 列表） */
  generateFromBiz: (bizType: QcBizType, bizId: number) =>
    post<number[]>(`/qc/inspects/from/${bizType}/${bizId}`),
  /** 挂点：来源单是否还有未完成检验（审核卡点） */
  pending: (bizType: QcBizType, bizId: number) =>
    get<InspectPendingResult>('/qc/inspects/pending', { bizType, bizId }),
}

/* ==================== 不合格品处置 ==================== */

export interface QcRejectRow {
  id: number
  rejectNo: string
  inspectId: number
  inspectNo: string
  materialId: number
  materialCode: string | null
  materialName: string | null
  spec: string | null
  unit: string | null
  qty: number
  decision: RejectDecision
  disposeRemark: string | null
  approver: string | null
  createTime: string
}

export interface QcRejectPageParams {
  pageNum: number
  pageSize: number
  keyword?: string
  materialId?: number
  decision?: RejectDecision
}

export interface QcRejectSavePayload {
  inspectId: number
  qty: number
  decision: RejectDecision
  disposeRemark?: string
  approver?: string
}

/** 可生成处置单的检验单选项 */
export interface RejectSource {
  inspectId: number
  inspectNo: string
  materialCode: string | null
  materialName: string | null
  unqualifiedQty: number
  disposedQty: number
  rejectableQty: number
}

export const rejectApi = {
  page: (params: QcRejectPageParams) => get<PageResult<QcRejectRow>>('/qc/rejects', { ...params }),
  get: (id: number) => get<QcRejectRow>(`/qc/rejects/${id}`),
  sources: () => get<RejectSource[]>('/qc/rejects/sources'),
  create: (payload: QcRejectSavePayload) => post<number>('/qc/rejects', payload),
  update: (id: number, payload: QcRejectSavePayload) => put<void>(`/qc/rejects/${id}`, payload),
  remove: (id: number) => del<void>(`/qc/rejects/${id}`),
}

/* ==================== 质量统计 ==================== */

export interface QcMaterialStat {
  materialId: number
  materialCode: string | null
  materialName: string | null
  sampleQty: number
  qualifiedQty: number
  passRate: number
}

export interface QcSupplierStat {
  supplierId: number
  supplierName: string | null
  sampleQty: number
  qualifiedQty: number
  passRate: number
}

export interface QcTrendPoint {
  date: string
  count: number
  sampleQty: number
}

export interface QcStatistics {
  fromDate: string
  toDate: string
  materialStats: QcMaterialStat[]
  supplierStats: QcSupplierStat[]
  trend: QcTrendPoint[]
}

export const statisticsApi = {
  get: (from?: string, to?: string) => get<QcStatistics>('/qc/statistics', { from, to }),
}
