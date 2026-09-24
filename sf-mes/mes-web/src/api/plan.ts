import { get, post, put, del } from '@/api/request'
import type { PageResult } from '@/api/request'

/**
 * 计划模块 API：生产工单/工序任务/齐套分析。类型与后端 com.mes.plan VO/Form 对齐。
 */

/** 生产工单列表行 */
export interface Workorder {
  id: number
  woNo: string
  materialId: number
  materialCode: string | null
  materialName: string | null
  qty: number
  bomId: number
  bomVersion: string
  routingId: number | null
  warehouseId: number | null
  planStartDate: string | null
  planEndDate: string | null
  status: string
  finishQty: number
  scrapQty: number
  snMode: string
  sourceType: string | null
  sourceNo: string | null
  remark: string | null
  createTime: string
}

/** 工序任务行 */
export interface WoStep {
  id: number
  workorderId: number
  seqNo: number
  operationId: number
  operationName: string
  workcenterId: number | null
  workcenterName: string | null
  planQty: number
  status: string
  assigneeName: string | null
  finishQty: number
  scrapQty: number
  reworkQty: number
  qcFlag: number
}

/** 用料需求行 */
export interface WoMaterial {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  reqQty: number
  issuedQty: number
}

/** 工单详情 */
export interface WorkorderDetail extends Workorder {
  closeRemark: string | null
  steps: WoStep[]
  materials: WoMaterial[]
}

/** 工单表单 */
export interface WorkorderForm {
  materialId: number
  qty: number
  bomId?: number | null
  routingId?: number | null
  warehouseId?: number | null
  planStartDate?: string | null
  planEndDate?: string | null
  snMode?: string
  remark?: string
}

/** 齐套分析记录 */
export interface KitCheck {
  id: number
  checkNo: string
  workorderId: number
  woNo: string
  result: string
  shortCount: number
  createTime: string
}

/** 齐套分析明细行 */
export interface KitCheckItem {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  reqQty: number
  availableQty: number
  shortQty: number
  result: string
}

/** 齐套分析详情 */
export interface KitCheckDetail extends KitCheck {
  items: KitCheckItem[]
}

/** 工单状态 -> 中文 */
export const WO_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  RELEASED: '已下达',
  PRODUCING: '生产中',
  FINISHED: '已完工',
  CLOSED: '已关闭',
}

/** 工序状态 -> 中文 */
export const STEP_STATUS: Record<string, string> = {
  PENDING_DISPATCH: '待派工',
  DISPATCHED: '已派工',
  PROCESSING: '加工中',
  FINISHED: '已完工',
}

export function getWorkorders(params: Record<string, unknown>) {
  return get<PageResult<Workorder>>('/plan/workorders', params)
}

export function getWorkorderDetail(id: number) {
  return get<WorkorderDetail>(`/plan/workorders/${id}`)
}

export function createWorkorder(form: WorkorderForm) {
  return post<number>('/plan/workorders', form)
}

export function updateWorkorder(id: number, form: WorkorderForm) {
  return put<void>(`/plan/workorders/${id}`, form)
}

export function deleteWorkorder(id: number) {
  return del<void>(`/plan/workorders/${id}`)
}

export function releaseWorkorder(id: number) {
  return put<void>(`/plan/workorders/${id}/release`)
}

export function finishWorkorder(id: number) {
  return put<void>(`/plan/workorders/${id}/finish`)
}

export function closeWorkorder(id: number, closeRemark?: string) {
  const qs = closeRemark ? `?closeRemark=${encodeURIComponent(closeRemark)}` : ''
  return put<void>(`/plan/workorders/${id}/close${qs}`)
}

export function getStepsByWorkorder(workorderId: number) {
  return get<WoStep[]>(`/plan/steps/workorder/${workorderId}`)
}

export function dispatchStep(id: number, assigneeId: number, assigneeName: string) {
  return put<void>(`/plan/steps/${id}/dispatch?assigneeId=${assigneeId}&assigneeName=${encodeURIComponent(assigneeName)}`)
}

export function runKitCheck(workorderId: number) {
  return post<number>(`/plan/kit-checks/${workorderId}`)
}

export function getKitChecks(params: Record<string, unknown>) {
  return get<PageResult<KitCheck>>('/plan/kit-checks', params)
}

export function getLatestKitCheck(workorderId: number) {
  return get<KitCheckDetail | null>(`/plan/kit-checks/latest/${workorderId}`)
}

// ===================== 排程 =====================

export interface ScheduleVO {
  id: number
  woStepId: number
  workorderId: number
  workcenterId: number
  planStartTime: string | null
  planEndTime: string | null
  lockFlag: number
}

export function getSchedules(workcenterId: number, from?: string, to?: string) {
  return get<ScheduleVO[]>('/plan/schedules', { workcenterId, from: from || undefined, to: to || undefined })
}
