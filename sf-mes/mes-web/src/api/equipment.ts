import { get, post, put, del } from '@/api/request'
import type { PageResult } from '@/api/request'

/**
 * 设备模块 API：台账/点检/保养维修/OEE。
 */

export interface Device {
  id: number
  code: string
  name: string
  model: string | null
  workcenterId: number | null
  workcenterName: string | null
  status: string
  location: string | null
  buyDate: string | null
  remark: string | null
}

export interface DeviceForm {
  name: string
  model?: string
  workcenterId?: number | null
  location?: string
  buyDate?: string | null
  remark?: string
}

export interface EqpCheck {
  id: number
  checkNo: string
  deviceId: number
  checkerName: string
  result: string
  checkTime: string
  remark: string | null
}

export interface EqpMaintain {
  id: number
  maintainNo: string
  deviceId: number
  type: string
  planDate: string | null
  maintainer: string | null
  durationMinutes: number | null
  status: string
  content: string | null
  finishTime: string | null
}

export interface Oee {
  workcenterId: number | null
  workcenterName: string | null
  plannedMinutes: number
  runMinutes: number
  availability: number
  performance: number
  quality: number
  oee: number
}

export const DEVICE_STATUS: Record<string, string> = {
  RUNNING: '运行',
  IDLE: '闲置',
  DOWN: '停机',
  REPAIR: '维修',
}

export function getDevices(params: Record<string, unknown>) {
  return get<PageResult<Device>>('/equipment/devices', params)
}

export function createDevice(form: DeviceForm) {
  return post<number>('/equipment/devices', form)
}

export function updateDevice(id: number, form: DeviceForm) {
  return put<void>(`/equipment/devices/${id}`, form)
}

export function deleteDevice(id: number) {
  return del<void>(`/equipment/devices/${id}`)
}

export function changeDeviceStatus(id: number, status: string) {
  return put<void>(`/equipment/devices/${id}/status?status=${status}`)
}

export function getChecks(params: Record<string, unknown>) {
  return get<PageResult<EqpCheck>>('/equipment/checks', params)
}

export function recordCheck(form: { deviceId: number; checkerName: string; result: string; remark?: string }) {
  return post<number>('/equipment/checks', form)
}

export function getMaintains(params: Record<string, unknown>) {
  return get<PageResult<EqpMaintain>>('/equipment/maintains', params)
}

export function createMaintain(form: { deviceId: number; type: string; planDate?: string; content?: string }) {
  return post<number>('/equipment/maintains', form)
}

export function finishMaintain(id: number, maintainer?: string, durationMinutes?: number) {
  const qs = `?maintainer=${encodeURIComponent(maintainer ?? '')}&durationMinutes=${durationMinutes ?? 0}`
  return put<void>(`/equipment/maintains/${id}/finish${qs}`)
}

export function calcOee(params: Record<string, unknown>) {
  return get<Oee[]>('/equipment/oee', params)
}
