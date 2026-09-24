import { get, post, put, del, type PageResult } from './request'

/* ===================== 类型定义（与后端 VO 对齐） ===================== */

/** 通道类型 */
export type ChannelType = 'webhook' | 'dingtalk' | 'wecom'

/** 通知通道（后端 ChannelVO，凭据已脱敏） */
export interface NotifyChannel {
  id: number
  channelType: ChannelType
  channelName: string
  /** 脱敏凭据：键 → ****尾4位（webhookUrl 保留协议+host） */
  credentials: Record<string, string> | null
  /** 已配置的凭据键 */
  credentialKeys: string[] | null
  /** 事件订阅 bitmask：bit0 待办(1) / bit1 审批结果(2) / bit2 预警(4) */
  eventMask: number
  mobileBaseUrl: string | null
  status: number
  remark: string | null
  createTime: string
}

/** 通道表单（新增/修改；凭据平铺字段按通道类型条件必填） */
export interface ChannelFormValues {
  channelType: ChannelType
  channelName: string
  webhookUrl?: string
  corpId?: string
  agentId?: string
  appSecret?: string
  secret?: string
  token?: string
  aesKey?: string
  eventMask: number
  mobileBaseUrl?: string
  /** 状态：1 启用 0 停用 */
  status: number
  remark?: string
}

/** 发送日志（后端 SendLogVO） */
export interface NotifySendLog {
  id: number
  channelId: number | null
  channelType: ChannelType
  eventType: 'TODO' | 'APPROVAL' | 'WARN' | 'TEST'
  receiverId: number | null
  receiver: string | null
  title: string
  content: string | null
  success: number
  errorMsg: string | null
  costMs: number | null
  createTime: string
}

/** 用户绑定（后端 BindingVO） */
export interface NotifyBinding {
  id: number
  userId: number
  userName: string | null
  channel: 'dingtalk' | 'wecom'
  externalId: string
  createTime: string
}

/** 用户绑定表单 */
export interface BindingFormValues {
  userId: number
  channel: 'dingtalk' | 'wecom'
  externalId: string
}

/* ===================== 展示映射 ===================== */

/** 通道类型标签 */
export const CHANNEL_TYPE_MAP: Record<ChannelType, string> = {
  webhook: '群机器人',
  dingtalk: '钉钉应用',
  wecom: '企微应用',
}

/** 事件位 → 名称（与后端 event_mask 对齐） */
export const EVENT_BITS: { bit: number; label: string }[] = [
  { bit: 1, label: '待办推送' },
  { bit: 2, label: '审批结果' },
  { bit: 4, label: '预警' },
]

/** 发送日志事件类型标签 */
export const EVENT_TYPE_MAP: Record<NotifySendLog['eventType'], string> = {
  TODO: '待办',
  APPROVAL: '审批结果',
  WARN: '预警',
  TEST: '测试',
}

/* ===================== 通道管理 ===================== */

/** GET /notify/channels 分页 */
export const pageChannels = (params: {
  pageNum: number
  pageSize: number
  keyword?: string
  channelType?: string
  status?: number
}) => get<PageResult<NotifyChannel>>('/notify/channels', params)

/** POST /notify/channels 新增 */
export const createChannel = (data: ChannelFormValues) => post<number>('/notify/channels', data)

/** PUT /notify/channels/{id} 修改 */
export const updateChannel = (id: number, data: ChannelFormValues) =>
  put<void>(`/notify/channels/${id}`, data)

/** PUT /notify/channels/{id}/status 启停 */
export const changeChannelStatus = (id: number, status: number) =>
  put<void>(`/notify/channels/${id}/status?status=${status}`)

/** DELETE /notify/channels/{id} 删除 */
export const deleteChannel = (id: number) => del<void>(`/notify/channels/${id}`)

/** POST /notify/channels/{id}/test 测试发送（钉钉/企微需指定接收人） */
export const testChannel = (id: number, receiverUserId?: number) =>
  post<NotifySendLog>(`/notify/channels/${id}/test${receiverUserId ? `?receiverUserId=${receiverUserId}` : ''}`)

/* ===================== 发送日志 ===================== */

/** GET /notify/send-logs 分页 */
export const pageSendLogs = (params: {
  pageNum: number
  pageSize: number
  channelId?: number
  channelType?: string
  eventType?: string
  success?: number
}) => get<PageResult<NotifySendLog>>('/notify/send-logs', params)

/* ===================== 用户绑定 ===================== */

/** GET /notify/bindings 分页 */
export const pageBindings = (params: {
  pageNum: number
  pageSize: number
  userId?: number
  channel?: string
  externalId?: string
}) => get<PageResult<NotifyBinding>>('/notify/bindings', params)

/** POST /notify/bindings 新增（同用户同通道唯一，重复覆盖） */
export const createBinding = (data: BindingFormValues) => post<number>('/notify/bindings', data)

/** DELETE /notify/bindings/{id} 删除 */
export const deleteBinding = (id: number) => del<void>(`/notify/bindings/${id}`)
