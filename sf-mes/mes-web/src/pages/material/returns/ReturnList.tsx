import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Drawer, Descriptions, Form, Input, InputNumber, Modal, Popconfirm, Row, Col, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import { auditReturn, createReturn, deleteReturn, getReturnDetail, getReturns, type MaterialReturn } from '@/api/material'
import { getMaterials, getWarehouses } from '@/api/basedata'
import { getWorkorders } from '@/api/plan'

/** 退料单页：余料/不良退回仓库（线边扣减 + 回传 ERP） */
export default function ReturnList() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [modalOpen, setModalOpen] = useState(false)
  const [detailId, setDetailId] = useState<number | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['material', 'returns', keyword, status],
    queryFn: () => getReturns({ pageNum: 1, pageSize: 20, keyword: keyword || undefined, status }),
  })
  const { data: workorders } = useQuery({
    queryKey: ['plan', 'workorders', 'for-return'],
    queryFn: () => getWorkorders({ pageNum: 1, pageSize: 100 }),
  })
  const { data: materials } = useQuery({ queryKey: ['basedata', 'materials', 'for-return'], queryFn: () => getMaterials({ pageNum: 1, pageSize: 200 }) })
  const { data: warehouses } = useQuery({ queryKey: ['basedata', 'warehouses', 'for-return'], queryFn: () => getWarehouses({ pageNum: 1, pageSize: 50 }) })
  const { data: detail } = useQuery({
    queryKey: ['material', 'return', detailId],
    queryFn: () => getReturnDetail(detailId!),
    enabled: detailId != null,
  })

  const createMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) =>
      createReturn({
        workorderId: (values.workorderId as number) ?? null,
        warehouseId: values.warehouseId as number,
        returnType: values.returnType as string,
        remark: values.remark as string,
        items: ((values.items as { materialId: number; batchNo?: string; qty: number }[]) ?? []).map((i) => ({ materialId: i.materialId, batchNo: i.batchNo ?? '', qty: i.qty })),
      }),
    onSuccess: () => {
      message.success('退料单已创建（草稿）')
      setModalOpen(false)
      queryClient.invalidateQueries({ queryKey: ['material', 'returns'] })
    },
  })
  const auditMutation = useMutation({
    mutationFn: (id: number) => auditReturn(id),
    onSuccess: () => {
      message.success('审核成功：线边仓扣减并回传 ERP')
      queryClient.invalidateQueries({ queryKey: ['material', 'returns'] })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteReturn(id),
    onSuccess: () => {
      message.success('删除成功')
      queryClient.invalidateQueries({ queryKey: ['material', 'returns'] })
    },
  })

  const columns: ColumnsType<MaterialReturn> = [
    { title: '退料单号', dataIndex: 'returnNo', width: 160 },
    { title: '工单号', dataIndex: 'woNo', width: 150, render: (v) => v ?? '-' },
    { title: '类型', dataIndex: 'returnType', width: 100, render: (v: string) => (v === 'REJECT' ? '退供应商' : '余料退库') },
    { title: 'ERP回传', dataIndex: 'erpPushed', width: 90, render: (v: number) => (v === 1 ? '已回传' : '未回传') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => <StatusTag value={v === 'AUDITED' ? '已审核' : '草稿'} semantic={v === 'AUDITED' ? 'success' : 'default'} />,
    },
    {
      title: '操作',
      width: 200,
      render: (_, r) => (
        <Space size={4}>
          <Button size="small" type="link" onClick={() => setDetailId(r.id)}>详情</Button>
          {r.status === 'DRAFT' && (
            <>
              <HasPerm code="material:return:audit">
                <Popconfirm title="审核后线边仓扣减并回传 ERP，确认？" onConfirm={() => auditMutation.mutate(r.id)}>
                  <Button size="small" type="link">审核</Button>
                </Popconfirm>
              </HasPerm>
              <HasPerm code="material:return:delete">
                <Popconfirm title="确认删除草稿退料单？" onConfirm={() => deleteMutation.mutate(r.id)}>
                  <Button size="small" type="link" danger icon={<DeleteOutlined />}>删除</Button>
                </Popconfirm>
              </HasPerm>
            </>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="退料单"
        subtitle="余料/不良退回仓库：审核后线边仓扣减并回传 ERP 入库冲销"
        extra={
          <HasPerm code="material:return:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true) }}>新建退料单</Button>
          </HasPerm>
        }
      />
      <Table<MaterialReturn>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Space>
            <Input.Search placeholder="退料单号/工单号" allowClear style={{ width: 220 }} onSearch={setKeyword} />
            <Select allowClear placeholder="状态" style={{ width: 110 }} onChange={setStatus} options={[{ value: 'DRAFT', label: '草稿' }, { value: 'AUDITED', label: '已审核' }]} />
          </Space>
        )}
      />

      <Modal
        title="新建退料单"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        width={720}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => createMutation.mutate(v)}>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="workorderId" label="工单（余料退库可空）">
                <Select allowClear showSearch optionFilterProp="label" options={(workorders?.records ?? []).map((w) => ({ value: w.id, label: w.woNo }))} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="returnType" label="退料类型" initialValue="LEFTOVER">
                <Select options={[{ value: 'LEFTOVER', label: '余料退库' }, { value: 'REJECT', label: '退供应商' }]} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="warehouseId" label="退回仓库" rules={[{ required: true, message: '请选择仓库' }]}>
                <Select showSearch optionFilterProp="label" options={(warehouses?.records ?? []).map((w) => ({ value: w.id, label: w.name }))} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="退料行" required style={{ marginBottom: 0 }}>
            <Form.List name="items" initialValue={[{}]}>
              {(fields, { add, remove }) => (
                <>
                  {fields.map((field) => (
                    <Space key={field.key} style={{ display: 'flex', marginBottom: 4 }} align="baseline">
                      <Form.Item name={[field.name, 'materialId']} rules={[{ required: true, message: '物料' }]} noStyle>
                        <Select showSearch optionFilterProp="label" placeholder="物料" style={{ width: 220 }} options={(materials?.records ?? []).map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))} />
                      </Form.Item>
                      <Form.Item name={[field.name, 'batchNo']} noStyle>
                        <Input placeholder="批次号" style={{ width: 130 }} />
                      </Form.Item>
                      <Form.Item name={[field.name, 'qty']} rules={[{ required: true, message: '数量' }]} noStyle>
                        <InputNumber min={0.0001} placeholder="数量" />
                      </Form.Item>
                      <Button type="link" danger onClick={() => remove(field.name)}>删除</Button>
                    </Space>
                  ))}
                  <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})}>添加行</Button>
                </>
              )}
            </Form.List>
          </Form.Item>
        </Form>
      </Modal>

      <Drawer title={`退料单：${detail?.returnNo ?? ''}`} width={640} open={detailId != null} onClose={() => setDetailId(null)}>
        {detail && (
          <>
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="工单">{detail.woNo ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="状态">{detail.status === 'AUDITED' ? '已审核' : '草稿'}</Descriptions.Item>
            </Descriptions>
            <Table rowKey="id" size="small" style={{ marginTop: 12 }} columns={[
              { title: '物料', render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}` },
              { title: '批次', dataIndex: 'batchNo' },
              { title: '数量', dataIndex: 'qty', align: 'right' },
            ]} dataSource={detail.items ?? []} pagination={false} />
          </>
        )}
      </Drawer>
    </>
  )
}
