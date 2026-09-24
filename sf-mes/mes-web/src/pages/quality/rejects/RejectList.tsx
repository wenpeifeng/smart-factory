import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Form, InputNumber, Modal, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import HasPerm from '@/components/HasPerm'
import { REJECT_DECISION, createReject, getInspects, getRejects, type QcReject } from '@/api/quality'

/** 不合格处置单页：对已提交且不合格的检验单发起处置（返工/让步/报废/退货） */
export default function RejectList() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm()
  const [modalOpen, setModalOpen] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['quality', 'rejects'],
    queryFn: () => getRejects({ pageNum: 1, pageSize: 20 }),
  })
  // 可选来源：已提交且存在不合格数的检验单
  const { data: badInspects } = useQuery({
    queryKey: ['quality', 'inspects', 'for-reject'],
    queryFn: () => getInspects({ pageNum: 1, pageSize: 100, status: 1 }),
  })

  const createMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) =>
      createReject({
        inspectId: values.inspectId as number,
        qty: values.qty as number,
        decision: values.decision as number,
        disposeRemark: values.disposeRemark as string,
      }),
    onSuccess: () => {
      message.success('处置单已创建')
      setModalOpen(false)
      queryClient.invalidateQueries({ queryKey: ['quality', 'rejects'] })
    },
  })

  const columns: ColumnsType<QcReject> = [
    { title: '处置单号', dataIndex: 'rejectNo', width: 160 },
    { title: '检验单号', dataIndex: 'inspectNo', width: 160 },
    { title: '物料', render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}` },
    { title: '数量', dataIndex: 'qty', width: 90, align: 'right' },
    { title: '处置决定', dataIndex: 'decision', width: 100, render: (v: number) => REJECT_DECISION[v] ?? v },
    { title: '说明', dataIndex: 'disposeRemark', ellipsis: true },
  ]

  return (
    <>
      <PageHeader
        title="不合格处置"
        subtitle="对检验不合格品做处置决定：返工 / 让步接收 / 报废 / 退货（数量累计不超检验单不合格数）"
        extra={
          <HasPerm code="quality:reject:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true) }}>发起处置</Button>
          </HasPerm>
        }
      />
      <Table<QcReject>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
      />

      <Modal
        title="发起不合格处置"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => createMutation.mutate(v)}>
          <Form.Item name="inspectId" label="检验单（仅显示已提交）" rules={[{ required: true, message: '请选择检验单' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={(badInspects?.records ?? [])
                .filter((i) => (i.unqualifiedQty ?? 0) > 0)
                .map((i) => ({ value: i.id, label: `${i.inspectNo}（不合格 ${i.unqualifiedQty}）` }))}
            />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="qty" label="处置数量" rules={[{ required: true }]}>
              <InputNumber min={0.0001} />
            </Form.Item>
            <Form.Item name="decision" label="处置决定" rules={[{ required: true }]}>
              <Select style={{ width: 140 }} options={Object.entries(REJECT_DECISION).map(([value, label]) => ({ value: Number(value), label }))} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </>
  )
}
