import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  pageTodo,
  pageDone,
  pageMyInstances,
  approveTask,
  rejectTask,
  cancelInstance,
  type WfTask,
  type WfInstance,
} from '@/api/workflow'
import { BIZ_TYPE_OPTIONS, BizTypeTag, InstanceStatusTag, TaskStatusTag } from '@/pages/workflow/shared'

const { Title } = Typography

/** 审批弹窗状态：目标任务 + 动作（通过/驳回） */
interface ApproveTarget {
  task: WfTask
  action: 'approve' | 'reject'
}

/**
 * 我的待办页：待办/已办/我发起的 三栏。
 * 待办行内通过/驳回，弹窗填写审批意见（驳回必填）；我发起的可撤回运行中的流程。
 * 服务端态全部走 TanStack Query；写操作成功后失效相关缓存自动刷新。
 */
export default function TodoList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [tab, setTab] = useState<'todo' | 'done' | 'mine'>('todo')
  const [keyword, setKeyword] = useState('')
  const [bizType, setBizType] = useState<string | undefined>(undefined)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [target, setTarget] = useState<ApproveTarget | null>(null)
  const [form] = Form.useForm<{ opinion?: string }>()

  // 三个列表共用查询参数；tab 切换时复用同一份筛选条件
  const params = { pageNum, pageSize, keyword: keyword || undefined, bizType }
  const todoQuery = useQuery({
    queryKey: ['workflow', 'todo', params],
    queryFn: () => pageTodo(params),
    enabled: tab === 'todo',
  })
  const doneQuery = useQuery({
    queryKey: ['workflow', 'done', params],
    queryFn: () => pageDone(params),
    enabled: tab === 'done',
  })
  const mineQuery = useQuery({
    queryKey: ['workflow', 'mine', params],
    queryFn: () => pageMyInstances(params),
    enabled: tab === 'mine',
  })
  const current = tab === 'todo' ? todoQuery : tab === 'done' ? doneQuery : mineQuery

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['workflow'] })
    queryClient.invalidateQueries({ queryKey: ['workflow', 'msgs'] })
  }

  const handleMutation = useMutation({
    mutationFn: ({ task, action, opinion }: { task: WfTask; action: 'approve' | 'reject'; opinion?: string }) =>
      action === 'approve' ? approveTask(task.id, opinion) : rejectTask(task.id, opinion),
    onSuccess: () => {
      message.success(target?.action === 'approve' ? '已通过' : '已驳回')
      setTarget(null)
      form.resetFields()
      invalidate()
    },
  })

  const cancelMutation = useMutation({
    mutationFn: cancelInstance,
    onSuccess: () => {
      message.success('流程已撤回')
      invalidate()
    },
  })

  /** 任务列（待办/已办共用，待办带操作列） */
  const taskColumns = (pending: boolean): ColumnsType<WfTask> => [
    { title: '单号', dataIndex: 'bizNo', width: 170 },
    { title: '单据类型', dataIndex: 'bizType', width: 110, render: (v) => <BizTypeTag bizType={v} /> },
    { title: '流程', dataIndex: 'flowName', width: 150, ellipsis: true, render: (v) => v ?? '-' },
    { title: '当前节点', dataIndex: 'nodeName', width: 130 },
    { title: '发起人', dataIndex: 'startUserName', width: 100, render: (v) => v ?? '-' },
    ...(pending
      ? []
      : ([
          { title: '任务状态', dataIndex: 'status', width: 100, render: (_: unknown, r: WfTask) => <TaskStatusTag status={r.status} /> },
          { title: '审批意见', dataIndex: 'opinion', ellipsis: true, render: (v: string | null) => v ?? '-' },
          { title: '审批时间', dataIndex: 'approveTime', width: 165, render: (v: string | null) => v ?? '-' },
        ] as ColumnsType<WfTask>)),
    { title: '到达时间', dataIndex: 'createTime', width: 165 },
    ...(pending
      ? ([
          {
            title: '操作',
            key: 'action',
            width: 140,
            fixed: 'right',
            render: (_, record) => (
              <Space size={4}>
                <Button
                  type="link"
                  size="small"
                  onClick={() => {
                    setTarget({ task: record, action: 'approve' })
                    form.resetFields()
                  }}
                >
                  通过
                </Button>
                <Button
                  type="link"
                  size="small"
                  danger
                  onClick={() => {
                    setTarget({ task: record, action: 'reject' })
                    form.resetFields()
                  }}
                >
                  驳回
                </Button>
              </Space>
            ),
          },
        ] as ColumnsType<WfTask>)
      : []),
  ]

  /** 我发起的实例列 */
  const instanceColumns: ColumnsType<WfInstance> = [
    { title: '单号', dataIndex: 'bizNo', width: 170 },
    { title: '单据类型', dataIndex: 'bizType', width: 110, render: (v) => <BizTypeTag bizType={v} /> },
    { title: '流程', dataIndex: 'flowName', width: 150, ellipsis: true, render: (v) => v ?? '-' },
    { title: '状态', dataIndex: 'status', width: 100, render: (v) => <InstanceStatusTag status={v} /> },
    { title: '当前节点', dataIndex: 'currentNode', width: 130, render: (v) => v ?? '-' },
    { title: '发起时间', dataIndex: 'createTime', width: 165 },
    { title: '结束时间', dataIndex: 'endTime', width: 165, render: (v) => v ?? '-' },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        record.status === 'RUNNING' ? (
          <Popconfirm title="确认撤回该流程？" onConfirm={() => cancelMutation.mutate(record.id)}>
            <Button type="link" size="small" danger>
              撤回
            </Button>
          </Popconfirm>
        ) : (
          '-'
        ),
    },
  ]

  const filterBar = (
    <Space style={{ marginBottom: 16 }}>
      <Input.Search
        placeholder="单号模糊搜索"
        style={{ width: 220 }}
        allowClear
        onSearch={(v) => {
          setPageNum(1)
          setKeyword(v)
        }}
      />
      <Select
        placeholder="单据类型"
        style={{ width: 150 }}
        allowClear
        options={BIZ_TYPE_OPTIONS}
        value={bizType}
        onChange={(v) => {
          setPageNum(1)
          setBizType(v)
        }}
      />
    </Space>
  )

  const pagination = {
    total: current.data?.total ?? 0,
    current: current.data?.pageNum ?? pageNum,
    pageSize: current.data?.pageSize ?? pageSize,
    showSizeChanger: true,
    showTotal: (t: number) => `共 ${t} 条`,
    onChange: (p: number, s: number) => {
      setPageNum(p)
      setPageSize(s)
    },
  }

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        我的待办
      </Title>

      <Card>
        <Tabs
          activeKey={tab}
          onChange={(k) => {
            setTab(k as typeof tab)
            setPageNum(1)
          }}
          items={[
            {
              key: 'todo',
              label: `待办${todoQuery.data?.total != null ? ` (${todoQuery.data.total})` : ''}`,
              children: (
                <>
                  {filterBar}
                  <Table<WfTask>
                    rowKey="id"
                    columns={taskColumns(true)}
                    dataSource={todoQuery.data?.records ?? []}
                    loading={todoQuery.isLoading || todoQuery.isFetching}
                    pagination={pagination}
                  />
                </>
              ),
            },
            {
              key: 'done',
              label: '已办',
              children: (
                <>
                  {filterBar}
                  <Table<WfTask>
                    rowKey="id"
                    columns={taskColumns(false)}
                    dataSource={doneQuery.data?.records ?? []}
                    loading={doneQuery.isLoading || doneQuery.isFetching}
                    pagination={pagination}
                  />
                </>
              ),
            },
            {
              key: 'mine',
              label: '我发起的',
              children: (
                <>
                  {filterBar}
                  <Table<WfInstance>
                    rowKey="id"
                    columns={instanceColumns}
                    dataSource={mineQuery.data?.records ?? []}
                    loading={mineQuery.isLoading || mineQuery.isFetching}
                    pagination={pagination}
                  />
                </>
              ),
            },
          ]}
        />
      </Card>

      {/* 审批弹窗：通过/驳回共用，驳回必填意见 */}
      <Modal
        title={`${target?.action === 'approve' ? '通过' : '驳回'} - ${target?.task.bizNo ?? ''}`}
        open={!!target}
        onCancel={() => setTarget(null)}
        onOk={() =>
          form.validateFields().then((values) =>
            handleMutation.mutate({ task: target!.task, action: target!.action, opinion: values.opinion }),
          )
        }
        confirmLoading={handleMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item label="单据信息" style={{ marginBottom: 8 }}>
            <Space size={4} wrap>
              {target && <BizTypeTag bizType={target.task.bizType} />}
              <span>{target?.task.flowName ?? '-'}</span>
              <span>节点：{target?.task.nodeName ?? '-'}</span>
            </Space>
          </Form.Item>
          <Form.Item
            name="opinion"
            label="审批意见"
            rules={
              target?.action === 'reject'
                ? [{ required: true, message: '驳回时请填写审批意见' }]
                : undefined
            }
          >
            <Input.TextArea
              placeholder={target?.action === 'reject' ? '必填：驳回原因' : '选填：审批意见'}
              maxLength={500}
              rows={3}
              showCount
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
