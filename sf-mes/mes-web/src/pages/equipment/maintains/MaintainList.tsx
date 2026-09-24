import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Form, Input, Modal, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import { createMaintain, finishMaintain, getDevices, getMaintains, type EqpMaintain } from '@/api/equipment'

/** 保养/维修单页 */
export default function MaintainList() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm()
  const [type, setType] = useState<string | undefined>()
  const [status, setStatus] = useState<string | undefined>()
  const [modalOpen, setModalOpen] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['equipment', 'maintains', type, status],
    queryFn: () => getMaintains({ pageNum: 1, pageSize: 20, type, status }),
  })
  const { data: devices } = useQuery({ queryKey: ['equipment', 'devices', 'for-maintain'], queryFn: () => getDevices({ pageNum: 1, pageSize: 100 }) })

  const createMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) =>
      createMaintain({ deviceId: values.deviceId as number, type: values.type as string, content: values.content as string }),
    onSuccess: () => {
      message.success('单据已创建（计划）')
      setModalOpen(false)
      queryClient.invalidateQueries({ queryKey: ['equipment', 'maintains'] })
    },
  })
  const finishMutation = useMutation({
    mutationFn: (id: number) => finishMaintain(id, '维修工'),
    onSuccess: () => {
      message.success('已完成（设备恢复闲置）')
      queryClient.invalidateQueries({ queryKey: ['equipment', 'maintains'] })
    },
  })

  const columns: ColumnsType<EqpMaintain> = [
    { title: '单号', dataIndex: 'maintainNo', width: 170 },
    { title: '设备', render: (_, r) => (devices?.records ?? []).find((d) => d.id === r.deviceId)?.name ?? r.deviceId },
    { title: '类型', dataIndex: 'type', width: 80, render: (v: string) => (v === 'PM' ? '保养' : '维修') },
    { title: '计划日期', dataIndex: 'planDate', width: 110, render: (v) => v ?? '-' },
    { title: '执行人', dataIndex: 'maintainer', width: 100, render: (v) => v ?? '-' },
    { title: '状态', dataIndex: 'status', width: 90, render: (v: string) => <StatusTag value={v === 'DONE' ? '已完成' : '计划中'} semantic={v === 'DONE' ? 'success' : 'processing'} /> },
    {
      title: '操作',
      width: 90,
      render: (_, r) =>
        r.status === 'PLAN' && (
          <HasPerm code="equipment:maintain:finish">
            <Button size="small" type="link" onClick={() => finishMutation.mutate(r.id)}>完成</Button>
          </HasPerm>
        ),
    },
  ]

  return (
    <>
      <PageHeader
        title="保养 / 维修"
        subtitle="保养(PM)与维修计划；完成后设备自动恢复闲置态"
        extra={
          <HasPerm code="equipment:maintain:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true) }}>新建单据</Button>
          </HasPerm>
        }
      />
      <Table<EqpMaintain>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Space>
            <Select allowClear placeholder="类型" style={{ width: 100 }} onChange={setType} options={[{ value: 'PM', label: '保养' }, { value: 'REPAIR', label: '维修' }]} />
            <Select allowClear placeholder="状态" style={{ width: 110 }} onChange={setStatus} options={[{ value: 'PLAN', label: '计划中' }, { value: 'DONE', label: '已完成' }]} />
          </Space>
        )}
      />

      <Modal title="新建保养/维修单" open={modalOpen} onCancel={() => setModalOpen(false)} onOk={() => form.submit()} confirmLoading={createMutation.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={(v) => createMutation.mutate(v)}>
          <Form.Item name="deviceId" label="设备" rules={[{ required: true, message: '请选择设备' }]}>
            <Select showSearch optionFilterProp="label" options={(devices?.records ?? []).map((d) => ({ value: d.id, label: `${d.code} ${d.name}` }))} />
          </Form.Item>
          <Form.Item name="type" label="类型" initialValue="PM" rules={[{ required: true }]}>
            <Select style={{ width: 140 }} options={[{ value: 'PM', label: '保养' }, { value: 'REPAIR', label: '维修' }]} />
          </Form.Item>
          <Form.Item name="content" label="内容">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
