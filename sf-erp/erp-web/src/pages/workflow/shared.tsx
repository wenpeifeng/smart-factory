import { Tag } from 'antd'
import type { WfBizType, WfInstance, WfMessage, WfTask } from '@/api/workflow'
import { BIZ_TYPE_MAP } from '@/api/workflow'

/**
 * 工作流页面共享件：单据类型/实例/任务/消息状态标签映射。
 * 仅供 pages/workflow/* 使用，不对外暴露。
 */

/** 单据类型下拉选项 */
export const BIZ_TYPE_OPTIONS = Object.entries(BIZ_TYPE_MAP).map(([value, label]) => ({
  value: value as WfBizType,
  label,
}))

/** 单据类型标签 */
export function BizTypeTag({ bizType }: { bizType: WfBizType }) {
  return <Tag color="geekblue">{BIZ_TYPE_MAP[bizType] ?? bizType}</Tag>
}

/** 实例状态 -> 标签（与后端 wf_instance.status 一致） */
const INSTANCE_STATUS_MAP: Record<WfInstance['status'], { text: string; color: string }> = {
  RUNNING: { text: '审批中', color: 'processing' },
  AGREED: { text: '已同意', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已撤回', color: 'default' },
}

/** 实例状态标签 */
export function InstanceStatusTag({ status }: { status: WfInstance['status'] }) {
  const cfg = INSTANCE_STATUS_MAP[status] ?? { text: `未知(${status})`, color: 'default' }
  return <Tag color={cfg.color}>{cfg.text}</Tag>
}

/** 实例状态筛选选项 */
export const INSTANCE_STATUS_OPTIONS = Object.entries(INSTANCE_STATUS_MAP).map(([value, cfg]) => ({
  value,
  label: cfg.text,
}))

/** 任务状态 -> 标签（与后端 wf_task.status 一致） */
const TASK_STATUS_MAP: Record<WfTask['status'], { text: string; color: string }> = {
  PENDING: { text: '待审批', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已失效', color: 'default' },
}

/** 任务状态标签 */
export function TaskStatusTag({ status }: { status: WfTask['status'] }) {
  const cfg = TASK_STATUS_MAP[status] ?? { text: `未知(${status})`, color: 'default' }
  return <Tag color={cfg.color}>{cfg.text}</Tag>
}

/** 消息类型 -> 标签（与后端 wf_message.msg_type 一致） */
export const MSG_TYPE_MAP: Record<WfMessage['msgType'], { text: string; color: string }> = {
  TODO: { text: '新待办', color: 'processing' },
  APPROVE: { text: '通过', color: 'success' },
  REJECT: { text: '驳回', color: 'error' },
  WARN: { text: '预警', color: 'warning' },
  IDENTITY: { text: '人员变动', color: 'cyan' },
}

/** 消息类型标签 */
export function MsgTypeTag({ msgType }: { msgType: WfMessage['msgType'] }) {
  const cfg = MSG_TYPE_MAP[msgType] ?? { text: `未知(${msgType})`, color: 'default' }
  return <Tag color={cfg.color}>{cfg.text}</Tag>
}

/** 消息类型筛选选项 */
export const MSG_TYPE_OPTIONS = Object.entries(MSG_TYPE_MAP).map(([value, cfg]) => ({
  value,
  label: cfg.text,
}))
