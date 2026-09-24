import { get, post, put, del } from '@/api/request'
import type { PageResult } from '@/api/request'

/**
 * 质量模块 API：检验模板/检验单/不合格处置。类型与后端 com.mes.quality VO/Form 对齐。
 */

/** 检验模板 */
export interface QcTemplate {
  id: number
  templateCode: string
  templateName: string
  categoryId: number | null
  inspectType: string
  status: number
  remark: string | null
  items?: QcTemplateItem[]
}

export interface QcTemplateItem {
  id: number
  itemName: string
  standardValue: string | null
  lowerLimit: number | null
  upperLimit: number | null
  inspectMethod: string | null
  sampleRate: number | null
  seq: number | null
}

export interface QcTemplateForm {
  templateCode?: string
  templateName: string
  categoryId?: number | null
  inspectType: string
  remark?: string
  items: {
    itemName: string
    standardValue?: string
    lowerLimit?: number | null
    upperLimit?: number | null
    inspectMethod?: string
    sampleRate?: number | null
    seq?: number | null
    remark?: string
  }[]
}

/** 检验单 */
export interface QcInspect {
  id: number
  inspectNo: string
  inspectType: string
  sourceType: string | null
  sourceNo: string | null
  templateId: number
  workorderId: number | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  batchNo: string | null
  sampleQty: number
  qualifiedQty: number | null
  unqualifiedQty: number | null
  conclusion: number | null
  inspectorName: string | null
  inspectDate: string | null
  status: number
  submitTime: string | null
  remark: string | null
  items?: QcInspectItem[]
}

export interface QcInspectItem {
  id: number
  itemName: string
  standardValue: string | null
  lowerLimit: number | null
  upperLimit: number | null
  actualValue: string | null
  lineResult: number | null
  seq: number | null
}

export interface QcInspectForm {
  inspectType: string
  sourceType?: string
  sourceNo?: string
  sourceId?: number | null
  templateId: number
  workorderId?: number | null
  woStepId?: number | null
  materialId: number
  supplierId?: number | null
  batchNo?: string
  sampleQty: number
  inspectorName?: string
  remark?: string
}

/** 不合格处置单 */
export interface QcReject {
  id: number
  rejectNo: string
  inspectId: number
  inspectNo: string
  materialId: number
  materialCode: string | null
  materialName: string | null
  qty: number
  decision: number
  disposeRemark: string | null
  approver: string | null
  createTime: string
}

export const INSPECT_TYPE: Record<string, string> = { IQC: '来料检验', IPQC: '过程检验', FQC: '成品检验' }

export const REJECT_DECISION: Record<number, string> = { 1: '返工', 2: '让步接收', 3: '报废', 4: '退货' }

export function getTemplates(params: Record<string, unknown>) {
  return get<PageResult<QcTemplate>>('/quality/templates', params)
}

export function getTemplateDetail(id: number) {
  return get<QcTemplate>(`/quality/templates/${id}`)
}

export function createTemplate(form: QcTemplateForm) {
  return post<number>('/quality/templates', form)
}

export function updateTemplate(id: number, form: QcTemplateForm) {
  return put<void>(`/quality/templates/${id}`, form)
}

export function deleteTemplate(id: number) {
  return del<void>(`/quality/templates/${id}`)
}

export function changeTemplateStatus(id: number, status: number) {
  return put<void>(`/quality/templates/${id}/status?status=${status}`)
}

export function getInspects(params: Record<string, unknown>) {
  return get<PageResult<QcInspect>>('/quality/inspects', params)
}

export function getInspectDetail(id: number) {
  return get<QcInspect>(`/quality/inspects/${id}`)
}

export function createInspect(form: QcInspectForm) {
  return post<number>('/quality/inspects', form)
}

export function fillInspectValues(id: number, fills: { itemId: number; actualValue: string }[]) {
  return put<void>(`/quality/inspects/${id}/values`, fills)
}

export function submitInspect(id: number) {
  return put<void>(`/quality/inspects/${id}/submit`)
}

export function getRejects(params: Record<string, unknown>) {
  return get<PageResult<QcReject>>('/quality/rejects', params)
}

export function createReject(form: { inspectId: number; qty: number; decision: number; disposeRemark?: string; approver?: string }) {
  return post<number>('/quality/rejects', form)
}

// ===================== SPC =====================

/** SPC 规则 */
export interface QcSpcRule {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  itemName: string
  ucl: number
  lcl: number
  target: number | null
  ruleR1: number
  ruleR2: number
  ruleR3: number
  ruleR4: number
  ruleR5: number
  status: number
  remark: string | null
}

/** SPC 图表样本 */
export interface SpcSample {
  id: number
  value: number
  sampleTime: string
  inspectId: number
  violations: string | null
}

export interface SpcChart {
  rule: QcSpcRule
  center: number
  samples: SpcSample[]
}

export function getSpcRules(params: Record<string, unknown>) {
  return get<PageResult<QcSpcRule>>('/quality/spc/rules', params)
}

export function saveSpcRule(form: Partial<QcSpcRule> & { materialId: number; itemName: string; ucl: number; lcl: number }) {
  return post<number>('/quality/spc/rules', form)
}

export function deleteSpcRule(id: number) {
  return del<void>(`/quality/spc/rules/${id}`)
}

export function changeSpcRuleStatus(id: number, status: number) {
  return put<void>(`/quality/spc/rules/${id}/status?status=${status}`)
}

export function getSpcChart(id: number, limit = 50) {
  return get<SpcChart>(`/quality/spc/rules/${id}/chart`, { limit })
}
