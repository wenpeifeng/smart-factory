import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Form, Input, Modal, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import { getChecks, getDevices, recordCheck, type EqpCheck } from '@/api/equipment'

/** 设备点检记录页 */
export default function CheckList() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm()
  const [deviceId, setDeviceId] = useState<number | undefined>()
  const [modalOpen, setModalOpen] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['equipment', 'checks', deviceId],
    queryFn: () => getChecks({ pageNum: 1, pageSize: 20, deviceId }),
  })
  const { data: devices } = useQuery({ queryKey: ['equipment', 'devices', 'for-check'], queryFn: () => getDevices({ pageNum: 1, pageSize: 100 }) })

  const recordMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) =>
      recordCheck({ deviceId: values.deviceId as number, checkerName: values.checkerName as string, result: values.result as string, remark: values.remark as string }),
    onSuccess: () => {
      message.success('点检已登记')
      setModalOpen(false)
      queryClient.invalidateQueries({ queryKey: ['equipment', 'checks'] })
    },
  })

  const columns: ColumnsType<EqpCheck> = [
    { title: '点检单号', dataIndex: 'checkNo', width: 170 },
    { title: '设备', render: (_, r) => (devices?.records ?? []).find((d) => d.id === r.deviceId)?.name ?? r.deviceId },
    { title: '点检人', dataIndex: 'checkerName', width: 100 },
    { title: '结果', dataIndex: 'result', width: 90, render: (v: string) => <StatusTag value={v === 'OK' ? '合格' : '不合格'} semantic={v === 'OK' ? 'success' : 'error'} /> },
    { title: '点检时间', dataIndex: 'checkTime', width: 170 },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
  ]

  return (
    <>
      <PageHeader
        title="设备点检"
        subtitle="日常点检登记；结果 NG 自动把设备置为维修态"
        extra={
          <HasPerm code="equipment:check:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true) }}>登记点检</Button>
          </HasPerm>
        }
      />
      <Table<EqpCheck>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="按设备过滤"
            style={{ width: 220 }}
            onChange={setDeviceId}
            options={(devices?.records ?? []).map((d) => ({ value: d.id, label: `${d.code} ${d.name}` }))}
          />
        )}
      />

      <Modal title="登记点检" open={modalOpen} onCancel={() => setModalOpen(false)} onOk={() => form.submit()} confirmLoading={recordMutation.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={(v) => recordMutation.mutate(v)}>
          <Form.Item name="deviceId" label="设备" rules={[{ required: true, message: '请选择设备' }]}>
            <Select showSearch optionFilterProp="label" options={(devices?.records ?? []).map((d) => ({ value: d.id, label: `${d.code} ${d.name}` }))} />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="checkerName" label="点检人" rules={[{ required: true }]}>
              <Input style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="result" label="结果" initialValue="OK" rules={[{ required: true }]}>
              <Select style={{ width: 110 }} options={[{ value: 'OK', label: '正常' }, { value: 'NG', label: '异常' }]} />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="异常说明">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
