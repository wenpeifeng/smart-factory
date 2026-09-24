import { get, post, type PageResult } from './request'

/**
 * 系统集成模块 API（ERP-MES 集成监控：出站 outbox + 入站消息日志）。
 * 出参类型与后端 IntOutbox / IntMessageLog 实体严格对齐。
 */

/** outbox 状态 */
export type OutboxStatus = 'PENDING' | 'SUCCESS' | 'DEAD'

/** outbox 状态选项 */
export const OUTBOX_STATUS_OPTIONS: { value: OutboxStatus; label: string }[] = [
  { value: 'PENDING', label: '待发/重试中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'DEAD', label: '死信' },
]

/** outbox 业务类型选项 */
export const OUTBOX_BIZ_TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: 'PRODUCTION_ORDER', label: '生产订单下推' },
  { value: 'MATERIAL', label: '物料下推' },
  { value: 'BOM', label: 'BOM下推' },
  { value: 'ROUTING', label: '工艺路线下推' },
]

/** 入站消息业务类型选项 */
export const INBOUND_BIZ_TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: 'WORK_REPORT', label: '完工报工' },
  { value: 'PICKING', label: '领料' },
  { value: 'RETURN', label: '退料' },
  { value: 'QUALITY', label: '质检结果' },
  { value: 'ORDER_STATUS', label: '工单进度' },
]

/** 出站发件箱行（int_outbox） */
export interface OutboxRow {
  id: number
  bizType: string
  bizId: number | null
  eventTime: string
  payload: string
  status: OutboxStatus
  retryCount: number
  nextRetryTime: string | null
  error: string | null
  successTime: string | null
  createTime: string
}

/** 入站消息日志行（int_message_log） */
export interface InboundLogRow {
  id: number
  bizType: string
  bizKey: string
  eventTime: string
  payload: string
  status: 'SUCCESS' | 'DUPLICATE'
  error: string | null
  createTime: string
}

/** outbox 分页查询参数 */
export interface OutboxPageParams extends Record<string, unknown> {
  pageNum: number
  pageSize: number
  status?: string
  bizType?: string
}

/** 入站日志分页查询参数 */
export interface InboundLogPageParams extends Record<string, unknown> {
  pageNum: number
  pageSize: number
  bizType?: string
  status?: string
}

/** 集成监控 API */
export const integrationApi = {
  /** outbox 分页 */
  outboxPage: (params: OutboxPageParams) =>
    get<PageResult<OutboxRow>>('/integration/outbox', params),
  /** outbox 单条手动重试 */
  outboxRetry: (id: number) => post<void>(`/integration/outbox/${id}/retry`),
  /** 入站消息日志分页 */
  inboundLogPage: (params: InboundLogPageParams) =>
    get<PageResult<InboundLogRow>>('/integration/inbound-logs', params),
  /** 主数据手动全量对账推送 */
  pushMasterdata: () => post<string>('/integration/masterdata/push'),
}
