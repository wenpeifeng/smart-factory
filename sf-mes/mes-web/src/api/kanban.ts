import { get, post, put } from '@/api/request'
import type { PageResult } from '@/api/request'

/**
 * 看板模块 API：安灯呼叫。
 */

export interface Andon {
  id: number
  callNo: string
  workcenterId: number | null
  workcenterName: string | null
  deviceId: number | null
  type: string
  callerName: string
  message: string
  status: string
  callTime: string
  respondTime: string | null
  responder: string | null
  closeTime: string | null
  closeRemark: string | null
}

export const ANDON_TYPE: Record<string, string> = {
  DEVICE: '设备',
  MATERIAL: '物料',
  QUALITY: '质量',
  OTHER: '其他',
}

export const ANDON_STATUS: Record<string, string> = {
  OPEN: '待响应',
  RESPOND: '处理中',
  CLOSED: '已关闭',
}

export function getAndons(params: Record<string, unknown>) {
  return get<PageResult<Andon>>('/kanban/andons', params)
}

export function callAndon(form: { workcenterId?: number | null; deviceId?: number | null; type: string; callerName: string; message: string }) {
  return post<number>('/kanban/andons', form)
}

export function respondAndon(id: number, responder: string) {
  return put<void>(`/kanban/andons/${id}/respond?responder=${encodeURIComponent(responder)}`)
}

export function closeAndon(id: number, closeRemark?: string) {
  const qs = closeRemark ? `?closeRemark=${encodeURIComponent(closeRemark)}` : ''
  return put<void>(`/kanban/andons/${id}/close${qs}`)
}
