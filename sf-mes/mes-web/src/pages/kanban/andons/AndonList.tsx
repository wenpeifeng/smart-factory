import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { BellOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import { ANDON_STATUS, ANDON_TYPE, callAndon, closeAndon, getAndons, respondAndon, type Andon } from '@/api/kanban'
import { get } from '@/api/request'

interface Workcenter { id: number; code: string; name: string }

/** 安灯呼叫页：发起 + 闭环（OPEN→RESPOND→CLOSED） */
export default function AndonList() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm()
  const [status, setStatus] = useState<string | undefined>()
  const [type, setType] = useState<string | undefined>()
  const [modalOpen, setModalOpen] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['kanban', 'andons', status, type],
    queryFn: () => getAndons({ pageNum: 1, pageSize: 20, status, type }),
    refetchInterval: 30000,
  })
  const { data: workcenters } = useQuery({
    queryKey: ['basedata', 'workcenters', 'andon'],
    queryFn: () => get<Workcenter[]>('/basedata/workcenters/enabled'),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['kanban', 'andons'] })

  const callMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) =>
      callAndon({ workcenterId: (values.workcenterId as number) ?? null, type: values.type as string, callerName: values.callerName as string, message: values.message as string }),
    onSuccess: () => {
      message.success('呼叫已发起，等车间响应')
      setModalOpen(false)
      invalidate()
    },
  })
  const respondMutation = useMutation({
    mutationFn: (id: number) => respondAndon(id, '值班员'),
    onSuccess: () => {
      message.success('已响应')
      invalidate()
    },
  })
  const closeMutation = useMutation({
    mutationFn: (id: number) => closeAndon(id, '已处理'),
    onSuccess: () => {
      message.success('已关闭')
      invalidate()
    },
  })

  const columns: ColumnsType<Andon> = [
    { title: '呼叫单号', dataIndex: 'callNo', width: 170 },
    { title: '工作中心', dataIndex: 'workcenterName', render: (v) => v ?? '-' },
    { title: '类型', dataIndex: 'type', width: 80, render: (v: string) => ANDON_TYPE[v] ?? v },
    { title: '呼叫人', dataIndex: 'callerName', width: 90 },
    { title: '异常描述', dataIndex: 'message', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 90, render: (v: string) => <StatusTag value={ANDON_STATUS[v] ?? v} semantic={v === 'OPEN' ? 'error' : v === 'RESPOND' ? 'processing' : 'success'} /> },
    { title: '呼叫时间', dataIndex: 'callTime', width: 170 },
    {
      title: '操作',
      width: 140,
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'OPEN' && (
            <HasPerm code="kanban:andon:respond">
              <Button size="small" type="link" onClick={() => respondMutation.mutate(r.id)}>响应</Button>
            </HasPerm>
          )}
          {r.status !== 'CLOSED' && (
            <HasPerm code="kanban:andon:close">
              <Popconfirm title="确认关闭该呼叫？" onConfirm={() => closeMutation.mutate(r.id)}>
                <Button size="small" type="link">关闭</Button>
              </Popconfirm>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="安灯呼叫"
        subtitle="车间异常呼叫闭环：待响应 → 处理中 → 已关闭（30 秒自动刷新）"
        extra={
          <HasPerm code="kanban:andon:call">
            <Button type="primary" icon={<BellOutlined />} onClick={() => { form.resetFields(); setModalOpen(true) }}>发起呼叫</Button>
          </HasPerm>
        }
      />
      <Table<Andon>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Space>
            <Select allowClear placeholder="状态" style={{ width: 100 }} onChange={setStatus} options={Object.entries(ANDON_STATUS).map(([value, label]) => ({ value, label }))} />
            <Select allowClear placeholder="类型" style={{ width: 100 }} onChange={setType} options={Object.entries(ANDON_TYPE).map(([value, label]) => ({ value, label }))} />
          </Space>
        )}
      />

      <Modal title="发起安灯呼叫" open={modalOpen} onCancel={() => setModalOpen(false)} onOk={() => form.submit()} confirmLoading={callMutation.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={(v) => callMutation.mutate(v)}>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="type" label="类型" initialValue="DEVICE" rules={[{ required: true }]}>
              <Select style={{ width: 120 }} options={Object.entries(ANDON_TYPE).map(([value, label]) => ({ value, label }))} />
            </Form.Item>
            <Form.Item name="workcenterId" label="工作中心">
              <Select allowClear showSearch optionFilterProp="label" placeholder="可选" style={{ width: 180 }} options={(workcenters ?? []).map((w) => ({ value: w.id, label: w.name }))} />
            </Form.Item>
          </Space>
          <Form.Item name="callerName" label="呼叫人" rules={[{ required: true }]}>
            <Input style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="message" label="异常描述" rules={[{ required: true }]}>
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
