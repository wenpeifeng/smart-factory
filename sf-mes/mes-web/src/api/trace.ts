import { get, post } from '@/api/request'
import type { PageResult } from '@/api/request'

/**
 * 追溯模块 API：正反向链路 / 电子批记录。
 */

/** 追溯节点 */
export interface TraceNode {
  id: number
  targetNo: string
  targetType: string
  workorderId: number
  woNo: string | null
  woStepId: number | null
  materialId: number | null
  qty: number
  linkTime: string
}

/** 电子批记录 */
export interface TraceEbr {
  id: number
  ebrNo: string
  workorderId: number
  woNo: string
  materialId: number
  materialCode?: string | null
  materialName?: string | null
  batchNo: string | null
  content: string | null
  generateTime: string
}

export function traceForward(no: string) {
  return get<TraceNode[]>('/trace/links/forward', { no })
}

export function traceBackward(no: string) {
  return get<TraceNode[]>('/trace/links/backward', { no })
}

export function getEbrs(params: Record<string, unknown>) {
  return get<PageResult<TraceEbr>>('/trace/ebrs', params)
}

export function getEbrDetail(id: number) {
  return get<TraceEbr>(`/trace/ebrs/${id}`)
}

export function generateEbr(workorderId: number) {
  return post<number>(`/trace/ebrs/workorder/${workorderId}`)
}
