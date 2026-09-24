import { get, post, put, del, type PageResult } from './request'

/* ===================== 类型定义（与后端 VO 对齐） ===================== */

/** 流程节点（后端 WfNodeVO / WfDefinitionForm.NodeForm） */
export interface WfNode {
  id?: number
  nodeOrder: number
  nodeName: string
  /** 审批人类型：APPROVER角色 USER指定用户 */
  approverType: 'APPROVER' | 'USER'
  /** 审批人 ID：APPROVER=角色ID，USER=用户ID */
  approverId: number
  approverName?: string | null
  /** 条件表达式（如 amount>10000，可空） */
  conditionExpr?: string
  createTime?: string
}

/** 流程定义（后端 WfDefinitionVO，详情含节点子表） */
export interface WfDefinition {
  id: number
  flowCode: string
  flowName: string
  bizType: WfBizType
  status: number
  remark: string | null
  nodeCount?: number | null
  nodes?: WfNode[] | null
  createTime: string
}

/** 流程定义表单（新增/修改，节点整体提交） */
export interface WfDefinitionForm {
  id?: number
  flowCode: string
  flowName: string
  bizType: WfBizType
  status: number
  remark?: string
  nodes: WfNode[]
}

/** 单据类型（字典 wf_biz_type） */
export type WfBizType =
  | 'pur_order'
  | 'sal_order'
  | 'pur_instock'
  | 'sal_outstock'
  | 'mrp_work_order'
  | 'fin_payment'
  | 'fin_receipt'

/** 单据类型下拉/展示映射 */
export const BIZ_TYPE_MAP: Record<WfBizType, string> = {
  pur_order: '采购订单',
  sal_order: '销售订单',
  pur_instock: '采购入库单',
  sal_outstock: '销售出库单',
  mrp_work_order: '生产订单',
  fin_payment: '付款单',
  fin_receipt: '收款单',
}

/** 流程实例（后端 WfInstanceVO） */
export interface WfInstance {
  id: number
  definitionId: number
  flowName: string | null
  bizType: WfBizType
  bizId: number
  bizNo: string
  status: 'RUNNING' | 'AGREED' | 'REJECTED' | 'CANCELLED'
  currentNode: string | null
  startUserId: number
  startUserName: string | null
  createTime: string
  endTime: string | null
}

/** 审批任务（后端 WfTaskVO，待办/已办共用） */
export interface WfTask {
  id: number
  instanceId: number
  nodeId: number
  nodeOrder: number
  nodeName: string
  bizType: WfBizType
  bizNo: string
  bizId: number
  flowName: string | null
  startUserId: number
  startUserName: string | null
  instanceStatus: WfInstance['status']
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
  opinion: string | null
  approveTime: string | null
  createTime: string
}

/** 发起流程结果（后端 StartResultVO） */
export interface StartResult {
  status: 'SKIPPED' | 'RUNNING' | 'AGREED'
  instanceId: number | null
  message: string
}

/** 消息（后端 WfMessageVO） */
export interface WfMessage {
  id: number
  receiverId: number
  msgType: 'TODO' | 'APPROVE' | 'REJECT' | 'WARN'
  title: string
  content: string | null
  bizPath: string | null
  readFlag: number
  readTime: string | null
  createTime: string
}

/* ===================== 流程定义 ===================== */

/** GET /workflow/defs 分页 */
export const pageDefs = (params: {
  pageNum: number
  pageSize: number
  keyword?: string
  bizType?: string
  status?: number
}) => get<PageResult<WfDefinition>>('/workflow/defs', params)

/** GET /workflow/defs/{id} 详情（含节点） */
export const getDef = (id: number) => get<WfDefinition>(`/workflow/defs/${id}`)

/** POST /workflow/defs 新增 */
export const createDef = (data: WfDefinitionForm) => post<number>('/workflow/defs', data)

/** PUT /workflow/defs/{id} 修改（节点整体覆盖） */
export const updateDef = (id: number, data: WfDefinitionForm) =>
  put<void>(`/workflow/defs/${id}`, data)

/** DELETE /workflow/defs/{id} 删除（有审批中实例时后端拒绝） */
export const deleteDef = (id: number) => del<void>(`/workflow/defs/${id}`)

/** PUT /workflow/defs/{id}/status 启停 */
export const updateDefStatus = (id: number, status: number) =>
  put<void>(`/workflow/defs/${id}/status`, { status })

/* ===================== 流程实例 / 任务 ===================== */

/** GET /workflow/todo 我的待办分页 */
export const pageTodo = (params: { pageNum: number; pageSize: number; keyword?: string; bizType?: string }) =>
  get<PageResult<WfTask>>('/workflow/todo', params)

/** GET /workflow/done 我的已办分页 */
export const pageDone = (params: { pageNum: number; pageSize: number; keyword?: string; bizType?: string }) =>
  get<PageResult<WfTask>>('/workflow/done', params)

/** GET /workflow/instances 我发起的流程分页 */
export const pageMyInstances = (params: {
  pageNum: number
  pageSize: number
  keyword?: string
  bizType?: string
  status?: string
}) => get<PageResult<WfInstance>>('/workflow/instances', params)

/** POST /workflow/instances 发起流程 */
export const startFlow = (data: {
  bizType: WfBizType
  bizId: number
  bizNo: string
  vars?: Record<string, number | string | boolean>
}) => post<StartResult>('/workflow/instances', data)

/** PUT /workflow/instances/{id}/cancel 撤回（仅发起人） */
export const cancelInstance = (id: number) => put<void>(`/workflow/instances/${id}/cancel`)

/** POST /workflow/tasks/{id}/approve 通过（意见可空） */
export const approveTask = (id: number, opinion?: string) =>
  post<void>(`/workflow/tasks/${id}/approve`, { opinion })

/** POST /workflow/tasks/{id}/reject 驳回（意见可空） */
export const rejectTask = (id: number, opinion?: string) =>
  post<void>(`/workflow/tasks/${id}/reject`, { opinion })

/** 任务详情（后端 WfTaskDetailVO：单据/金额 + 意见历史，移动端审批页用） */
export interface WfTaskDetail extends WfTask {
  amount: number | null
  vars: string | null
  history: {
    nodeName: string
    nodeOrder: number
    approverId: number
    approverName: string | null
    status: WfTask['status']
    opinion: string | null
    approveTime: string | null
  }[]
}

/** GET /workflow/tasks/{id}/detail 任务详情 */
export const getTaskDetail = (id: number | string) =>
  get<WfTaskDetail>(`/workflow/tasks/${id}/detail`)

/* ===================== 消息中心 ===================== */

/** GET /workflow/messages 我的消息分页 */
export const pageMessages = (params: {
  pageNum: number
  pageSize: number
  msgType?: string
  unreadOnly?: boolean
}) => get<PageResult<WfMessage>>('/workflow/messages', params)

/** GET /workflow/messages/unread-count 未读数 */
export const unreadCount = () => get<number>('/workflow/messages/unread-count')

/** PUT /workflow/messages/{id}/read 标记已读 */
export const markRead = (id: number) => put<void>(`/workflow/messages/${id}/read`)

/** PUT /workflow/messages/read-all 全部已读（返回标记条数） */
export const markAllRead = () => put<number>('/workflow/messages/read-all')
