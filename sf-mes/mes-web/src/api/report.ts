import { get } from '@/api/request'

/**
 * 报表/驾驶舱/大屏 API。
 */

/** 看板大屏数据 */
export interface ScreenData {
  andons: { type: string; cnt: number }[]
  woStatus: { status: string; cnt: number }[]
  wipTop: { operationName: string; wipQty: number }[]
  woProgress: { woNo: string; materialName: string | null; planQty: number; finishQty: number; progressPct: number; status: string }[]
  dailyOutput: { d: string; goodQty: number; scrapQty: number }[]
  quality: { inspectType: string; sampleQty: number; qualifiedQty: number; unqualifiedQty: number }[]
  openAndonCount: number
}

/** 驾驶舱数据 */
export interface CockpitData {
  woStatus: { status: string; cnt: number }[]
  dailyOutput: { d: string; goodQty: number; scrapQty: number }[]
  quality: { inspectType: string; sampleQty: number; qualifiedQty: number; unqualifiedQty: number }[]
  shortWorkorders: number
  pendingPushes: number
  openAndons: number
}

export const WO_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  RELEASED: '已下达',
  PRODUCING: '生产中',
  FINISHED: '已完工',
  CLOSED: '已关闭',
}

export const ANDON_TYPE_LABEL: Record<string, string> = {
  DEVICE: '设备',
  MATERIAL: '物料',
  QUALITY: '质量',
  OTHER: '其他',
}

export function getScreen() {
  return get<ScreenData>('/report/screen')
}

export function getCockpit() {
  return get<CockpitData>('/report/cockpit')
}

export function getWoAchievement(params?: Record<string, unknown>) {
  return get<Record<string, unknown>[]>('/report/workorder-achievement', params)
}

export function getStationDetail(days = 7) {
  return get<Record<string, unknown>[]>('/report/station-detail', { days })
}

export function getQualitySummary() {
  return get<Record<string, unknown>[]>('/report/quality-summary')
}

export function downloadReportCsv(path: string, filename: string) {
  const token = (window as unknown as { __authToken?: string }).__authToken
  const a = document.createElement('a')
  a.href = `/api${path}${token ? '' : ''}`
  a.download = filename
  a.click()
}
