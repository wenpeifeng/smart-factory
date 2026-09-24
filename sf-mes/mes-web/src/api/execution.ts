import { get, post, put } from '@/api/request'
import type { PageResult } from '@/api/request'

/**
 * 执行模块 API：过站报工/WIP/返工报废。类型与后端 com.mes.execution VO/Form 对齐。
 */

/** 过站记录 */
export interface StationRecord {
  id: number
  recordNo: string
  workorderId: number
  woNo: string
  woStepId: number
  seqNo: number
  operationName: string
  workcenterId: number | null
  sn: string | null
  goodQty: number
  scrapQty: number
  reworkQty: number
  workMinutes: number | null
  workerName: string | null
  stationTime: string
  result: string
}

/** 过站报工表单 */
export interface StationReportForm {
  woStepId: number
  sn?: string
  keyComponentSn?: string
  goodQty: number
  scrapQty: number
  reworkQty?: number
  workMinutes?: number
  deviceId?: number | null
  teamId?: number | null
  workerName?: string
  result?: string
}

/** WIP 汇总行 */
export interface Wip {
  id: number
  workorderId: number
  woNo: string
  woStepId: number
  seqNo: number
  operationName: string
  workcenterId: number | null
  inputQty: number
  outputQty: number
  wipQty: number
  scrapQty: number
  reworkQty: number
  lastStationTime: string | null
}

/** 返工/报废单 */
export interface ReworkScrap {
  id: number
  orderNo: string
  type: string
  workorderId: number
  woNo: string
  woStepId: number
  operationName: string
  sn: string | null
  qty: number
  reason: string
  status: string
  handleRemark: string | null
  createTime: string
}

export function getStationRecords(params: Record<string, unknown>) {
  return get<PageResult<StationRecord>>('/execution/stations', params)
}

export function reportStation(form: StationReportForm) {
  return post<number>('/execution/stations', form)
}

export function getWips(params: Record<string, unknown>) {
  return get<PageResult<Wip>>('/execution/wips', params)
}

export function getReworkScraps(params: Record<string, unknown>) {
  return get<PageResult<ReworkScrap>>('/execution/rework-scraps', params)
}

export function createReworkScrap(form: { type: string; woStepId: number; sn?: string; qty: number; reason: string; qcRejectId?: number }) {
  return post<number>('/execution/rework-scraps', form)
}

export function handleReworkScrap(id: number, handleRemark?: string) {
  const qs = handleRemark ? `?handleRemark=${encodeURIComponent(handleRemark)}` : ''
  return put<void>(`/execution/rework-scraps/${id}/handle${qs}`)
}
