import { get, post } from '@/api/request'
import type { PageResult } from '@/api/request'

/**
 * 集成模块 API：回传推送日志（对账）/ 用户同步。
 */

/** 推送日志 */
export interface PushLog {
  id: number
  bizType: string
  bizId: number
  bizNo: string
  payload: string
  status: string
  retryCount: number
  maxRetry: number
  lastError: string | null
  pushTime: string | null
  successTime: string | null
  createTime: string
}

export const PUSH_BIZ_TYPE: Record<string, string> = {
  WORK_REPORT: '完工报工',
  PICKING: '领料',
  RETURN: '退料',
  QUALITY: '质检结果',
}

export function getPushLogs(params: Record<string, unknown>) {
  return get<PageResult<PushLog>>('/integration/push-logs', params)
}

export function syncAuthUsers() {
  return post<{ fetched: number; inserted: number; updated: number; disabled: number }>('/integration/auth/sync')
}
