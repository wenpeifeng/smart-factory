import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom'
import { Alert, Button, Card, Descriptions, Form, Input, List, Modal, Space, Tag } from 'antd'
import { CheckOutlined, CloseOutlined, LeftOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { MobileShell } from '@/mobile/shared'
import { BIZ_TYPE_MAP, approveTask, getTaskDetail, rejectTask, type WfBizType } from '@/api/workflow'

/** 任务状态标签映射（与桌面端 workflow/shared.tsx 同口径） */
const TASK_STATUS: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待审批', color: 'processing' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已驳回', color: 'error' },
  CANCELLED: { label: '已失效', color: 'default' },
}

/**
 * 移动端审批页（/m/approve/:taskId）。
 * <p>
 * 钉钉/企微/群机器人待办推送链接指向本页面（链接 = 通道配置的移动端基地址 + /m/approve/{taskId}）；
 * 登录守卫由移动路由统一包（未登录跳 /m/login 后回跳）。
 * 展示任务详情（单据/金额/发起人/意见历史），通过/驳回调既有 workflow 接口，
 * 复用与桌面端待办完全相同的审批语义（条件 UPDATE 防并发/或签失效/消息与通知事件链路）。
 */
export default function ApprovePage() {
  const { taskId } = useParams<{ taskId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [rejectOpen, setRejectOpen] = useState(false)
  const [rejectForm] = Form.useForm<{ opinion: string }>()
  const [submitting, setSubmitting] = useState(false)

  const { data: task, error } = useQuery({
    queryKey: ['mobile', 'task', taskId],
    queryFn: () => getTaskDetail(taskId!),
    enabled: !!taskId,
    retry: false,
  })

  /** 通过/驳回（调既有接口；成功后刷新详情展示终态） */
  const doApprove = async (approve: boolean, opinion?: string) => {
    if (!taskId) return
    setSubmitting(true)
    try {
      if (approve) await approveTask(Number(taskId), opinion)
      else await rejectTask(Number(taskId), opinion)
      queryClient.invalidateQueries({ queryKey: ['mobile', 'task', taskId] })
      Modal.success({
        title: approve ? '已通过' : '已驳回',
        content: approve ? '单据审批已通过，流程继续推进' : '单据已被驳回，发起人可在待办重新提交',
      })
    } catch {
      /* 错误提示由 axios 拦截器统一弹出 */
    } finally {
      setSubmitting(false)
    }
  }

  const pending = task?.status === 'PENDING'

  return (
    <MobileShell
      title="移动审批"
      extra={
        <Button type="text" size="small" icon={<LeftOutlined />} style={{ color: '#fff' }} onClick={() => navigate(-1)}>
          返回
        </Button>
      }
    >
      {error ? (
        <Alert type="error" showIcon message="任务加载失败" description={(error as Error).message} />
      ) : !task ? (
        <Card loading style={{ minHeight: 200 }} />
      ) : (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          {/* 单据信息 */}
          <Card size="small" title={`单据信息 · ${BIZ_TYPE_MAP[task.bizType as WfBizType] ?? task.bizType}`}>
            <Descriptions column={1} size="small" labelStyle={{ width: 90 }}>
              <Descriptions.Item label="单号">{task.bizNo}</Descriptions.Item>
              {task.amount != null && <Descriptions.Item label="金额">¥ {task.amount}</Descriptions.Item>}
              <Descriptions.Item label="发起人">{task.startUserName ?? task.startUserId}</Descriptions.Item>
              <Descriptions.Item label="当前节点">{task.nodeName}</Descriptions.Item>
              {task.flowName && <Descriptions.Item label="流程">{task.flowName}</Descriptions.Item>}
              <Descriptions.Item label="任务状态">
                <Tag color={TASK_STATUS[task.status]?.color}>{TASK_STATUS[task.status]?.label ?? task.status}</Tag>
                <Tag color={task.instanceStatus === 'RUNNING' ? 'processing' : 'default'}>{task.instanceStatus}</Tag>
              </Descriptions.Item>
            </Descriptions>
          </Card>

          {/* 意见历史 */}
          <Card size="small" title="审批记录">
            <List
              size="small"
              dataSource={task.history ?? []}
              locale={{ emptyText: '暂无审批记录' }}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={
                      <Space size={8}>
                        <span>{item.nodeName}</span>
                        <Tag color={TASK_STATUS[item.status]?.color} style={{ marginRight: 0 }}>
                          {TASK_STATUS[item.status]?.label ?? item.status}
                        </Tag>
                      </Space>
                    }
                    description={
                      <>
                        <div style={{ color: '#5a5f66' }}>{item.approverName ?? item.approverId}</div>
                        {item.opinion ? <div style={{ marginTop: 2 }}>{item.opinion}</div> : null}
                        {item.approveTime ? (
                          <div style={{ color: '#8c939c', fontSize: 12, marginTop: 2 }}>{item.approveTime}</div>
                        ) : null}
                      </>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>

          {/* 审批操作（仅待办可操作；非本人待办后端会拒绝） */}
          {pending && (
            <Space direction="vertical" size={10} style={{ width: '100%' }}>
              <Button
                type="primary"
                size="large"
                block
                icon={<CheckOutlined />}
                loading={submitting}
                onClick={() => Modal.confirm({ title: '确认通过该单据？', onOk: () => doApprove(true) })}
              >
                通过
              </Button>
              <Button
                danger
                size="large"
                block
                icon={<CloseOutlined />}
                loading={submitting}
                onClick={() => {
                  rejectForm.resetFields()
                  setRejectOpen(true)
                }}
              >
                驳回
              </Button>
            </Space>
          )}
        </Space>
      )}

      {/* 驳回意见（必填） */}
      <Modal
        title="驳回意见"
        open={rejectOpen}
        onCancel={() => setRejectOpen(false)}
        onOk={() =>
          rejectForm.validateFields().then((v) => {
            setRejectOpen(false)
            doApprove(false, v.opinion)
          })
        }
        destroyOnClose
      >
        <Form form={rejectForm} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item
            name="opinion"
            label="意见"
            rules={[{ required: true, message: '驳回必须填写意见' }]}
          >
            <Input.TextArea rows={3} maxLength={500} placeholder="请填写驳回原因" />
          </Form.Item>
        </Form>
      </Modal>
    </MobileShell>
  )
}
