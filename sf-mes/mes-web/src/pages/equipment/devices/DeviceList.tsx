import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import { DEVICE_STATUS, changeDeviceStatus, createDevice, deleteDevice, getDevices, updateDevice, type Device, type DeviceForm } from '@/api/equipment'
import { get } from '@/api/request'

interface Workcenter { id: number; code: string; name: string }

/** 设备台账页 */
export default function DeviceList() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm<DeviceForm>()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Device | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['equipment', 'devices', keyword, status],
    queryFn: () => getDevices({ pageNum: 1, pageSize: 20, keyword: keyword || undefined, status }),
  })
  const { data: workcenters } = useQuery({
    queryKey: ['basedata', 'workcenters', 'all'],
    queryFn: () => get<Workcenter[]>('/basedata/workcenters/enabled'),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['equipment', 'devices'] })

  const saveMutation = useMutation({
    mutationFn: async (values: DeviceForm) => {
      if (editing) await updateDevice(editing.id, values)
      else await createDevice(values)
    },
    onSuccess: () => {
      message.success('保存成功')
      setModalOpen(false)
      invalidate()
    },
  })
  const statusMutation = useMutation({
    mutationFn: ({ id, next }: { id: number; next: string }) => changeDeviceStatus(id, next),
    onSuccess: () => {
      message.success('状态已更新')
      invalidate()
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteDevice(id),
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const columns: ColumnsType<Device> = [
    { title: '编码', dataIndex: 'code', width: 150 },
    { title: '名称', dataIndex: 'name' },
    { title: '型号', dataIndex: 'model', render: (v) => v ?? '-' },
    { title: '工作中心', dataIndex: 'workcenterName', render: (v) => v ?? '-' },
    { title: '位置', dataIndex: 'location', render: (v) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v: string) => <StatusTag value={DEVICE_STATUS[v] ?? v} semantic={v === 'RUNNING' ? 'processing' : v === 'REPAIR' || v === 'DOWN' ? 'error' : 'default'} />,
    },
    {
      title: '操作',
      width: 260,
      render: (_, r) => (
        <Space size={4}>
          <HasPerm code="equipment:device:edit">
            <Button size="small" type="link" icon={<EditOutlined />} onClick={() => { setEditing(r); form.setFieldsValue({ name: r.name, model: r.model ?? undefined, workcenterId: r.workcenterId ?? undefined, location: r.location ?? undefined }); setModalOpen(true) }}>编辑</Button>
            <Select size="small" value={r.status} style={{ width: 90 }} onChange={(next) => statusMutation.mutate({ id: r.id, next })} options={Object.entries(DEVICE_STATUS).map(([value, label]) => ({ value, label }))} />
          </HasPerm>
          <HasPerm code="equipment:device:delete">
            <Popconfirm title="确认删除设备？" onConfirm={() => deleteMutation.mutate(r.id)}>
              <Button size="small" type="link" danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          </HasPerm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="设备台账"
        subtitle="车间设备档案与运行状态（过站报工可关联设备，点检 NG 自动转维修态）"
        extra={
          <HasPerm code="equipment:device:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); form.resetFields(); setModalOpen(true) }}>新增设备</Button>
          </HasPerm>
        }
      />
      <Table<Device>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Space>
            <Input.Search placeholder="编码/名称/型号" allowClear style={{ width: 220 }} onSearch={setKeyword} />
            <Select allowClear placeholder="状态" style={{ width: 100 }} onChange={setStatus} options={Object.entries(DEVICE_STATUS).map(([value, label]) => ({ value, label }))} />
          </Space>
        )}
      />

      <Modal title={editing ? '编辑设备' : '新增设备'} open={modalOpen} onCancel={() => setModalOpen(false)} onOk={() => form.submit()} confirmLoading={saveMutation.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={(v) => saveMutation.mutate(v)}>
          <Form.Item name="name" label="设备名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={100} />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="model" label="规格型号">
              <Input />
            </Form.Item>
            <Form.Item name="location" label="安装位置">
              <Input />
            </Form.Item>
          </Space>
          <Form.Item name="workcenterId" label="所属工作中心">
            <Select allowClear showSearch optionFilterProp="label" options={(workcenters ?? []).map((w) => ({ value: w.id, label: w.name }))} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
