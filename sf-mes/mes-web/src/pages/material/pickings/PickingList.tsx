import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Col, Drawer, Descriptions, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import { auditPicking, createPicking, deletePicking, getPickingDetail, getPickings, type Picking, type PickingItem } from '@/api/material'
import { getMaterials, getWarehouses } from '@/api/basedata'
import { getWorkorders } from '@/api/plan'

interface ItemForm { materialId: number; batchNo: string; qty: number; remark?: string }

/** 领料单页：列表 + 创建（头+行）+ 审核（线边入库/回写工单/回传 ERP）+ 详情 */
export default function PickingList() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [modalOpen, setModalOpen] = useState(false)
  const [detailId, setDetailId] = useState<number | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['material', 'pickings', keyword, status],
    queryFn: () => getPickings({ pageNum: 1, pageSize: 20, keyword: keyword || undefined, status }),
  })
  const { data: workorders } = useQuery({
    queryKey: ['plan', 'workorders', 'for-picking'],
    queryFn: () => getWorkorders({ pageNum: 1, pageSize: 100 }),
  })
  const { data: materials } = useQuery({
    queryKey: ['basedata', 'materials', 'for-picking'],
    queryFn: () => getMaterials({ pageNum: 1, pageSize: 200 }),
  })
  const { data: warehouses } = useQuery({
    queryKey: ['basedata', 'warehouses', 'enabled'],
    queryFn: () => getWarehouses({ pageNum: 1, pageSize: 50 }),
  })
  const { data: detail } = useQuery({
    queryKey: ['material', 'picking', detailId],
    queryFn: () => getPickingDetail(detailId!),
    enabled: detailId != null,
  })

  const createMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) =>
      createPicking({
        workorderId: values.workorderId as number,
        warehouseId: values.warehouseId as number,
        remark: values.remark as string,
        items: ((values.items as ItemForm[]) ?? []).map((i) => ({ materialId: i.materialId, batchNo: i.batchNo || '-', qty: i.qty })),
      }),
    onSuccess: () => {
      message.success('领料单已创建（草稿）')
      setModalOpen(false)
      queryClient.invalidateQueries({ queryKey: ['material', 'pickings'] })
    },
  })

  const auditMutation = useMutation({
    mutationFn: (id: number) => auditPicking(id),
    onSuccess: () => {
      message.success('审核成功：线边仓入库并回传 ERP')
      queryClient.invalidateQueries({ queryKey: ['material', 'pickings'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deletePicking(id),
    onSuccess: () => {
      message.success('删除成功')
      queryClient.invalidateQueries({ queryKey: ['material', 'pickings'] })
    },
  })

  const columns: ColumnsType<Picking> = [
    { title: '领料单号', dataIndex: 'pickNo', width: 160 },
    { title: '工单号', dataIndex: 'woNo', width: 150 },
    { title: '审核时间', dataIndex: 'auditTime', width: 170, render: (v) => v ?? '-' },
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
              <HasPerm code="material:picking:audit">
                <Popconfirm title="审核后线边仓入库并回传 ERP，确认？" onConfirm={() => auditMutation.mutate(r.id)}>
                  <Button size="small" type="link">审核</Button>
                </Popconfirm>
              </HasPerm>
              <HasPerm code="material:picking:delete">
                <Popconfirm title="确认删除草稿领料单？" onConfirm={() => deleteMutation.mutate(r.id)}>
                  <Button size="small" type="link" danger icon={<DeleteOutlined />}>删除</Button>
                </Popconfirm>
              </HasPerm>
            </>
          )}
        </Space>
      ),
    },
  ]

  const itemColumns: ColumnsType<PickingItem> = [
    { title: '物料', render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}` },
    { title: '批次', dataIndex: 'batchNo', render: (v) => v || '-' },
    { title: '数量', dataIndex: 'qty', align: 'right' },
  ]

  return (
    <>
      <PageHeader
        title="领料单"
        subtitle="车间向仓库领料：审核后线边仓入库、回写工单已领数量并回传 ERP（幂等+重试）"
        extra={
          <HasPerm code="material:picking:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true) }}>新建领料单</Button>
          </HasPerm>
        }
      />
      <Table<Picking>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Space>
            <Input.Search placeholder="领料单号/工单号" allowClear style={{ width: 220 }} onSearch={setKeyword} />
            <Select allowClear placeholder="状态" style={{ width: 110 }} onChange={setStatus} options={[{ value: 'DRAFT', label: '草稿' }, { value: 'AUDITED', label: '已审核' }]} />
          </Space>
        )}
      />

      <Modal
        title="新建领料单"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        width={720}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => createMutation.mutate(v)}>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="workorderId" label="工单" rules={[{ required: true, message: '请选择工单' }]}>
                <Select showSearch optionFilterProp="label" options={(workorders?.records ?? []).map((w) => ({ value: w.id, label: `${w.woNo}` }))} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="warehouseId" label="发料仓库" rules={[{ required: true, message: '请选择仓库' }]}>
                <Select showSearch optionFilterProp="label" options={(warehouses?.records ?? []).map((w) => ({ value: w.id, label: w.name }))} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="领料行（批次不管理物料批次填 -）" required style={{ marginBottom: 0 }}>
            <Form.List name="items" initialValue={[{ batchNo: '-' }]}>
              {(fields, { add, remove }) => (
                <>
                  {fields.map((field) => (
                    <Space key={field.key} style={{ display: 'flex', marginBottom: 4 }} align="baseline">
                      <Form.Item name={[field.name, 'materialId']} rules={[{ required: true, message: '物料' }]} noStyle>
                        <Select showSearch optionFilterProp="label" placeholder="物料" style={{ width: 220 }} options={(materials?.records ?? []).map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))} />
                      </Form.Item>
                      <Form.Item name={[field.name, 'batchNo']} initialValue="-" noStyle>
                        <Input placeholder="批次号" style={{ width: 140 }} />
                      </Form.Item>
                      <Form.Item name={[field.name, 'qty']} rules={[{ required: true, message: '数量' }]} noStyle>
                        <InputNumber min={0.0001} placeholder="数量" />
                      </Form.Item>
                      <Button type="link" danger onClick={() => remove(field.name)}>删除</Button>
                    </Space>
                  ))}
                  <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({ batchNo: '-' })}>添加行</Button>
                </>
              )}
            </Form.List>
          </Form.Item>
        </Form>
      </Modal>

      <Drawer title={`领料单：${detail?.pickNo ?? ''}`} width={640} open={detailId != null} onClose={() => setDetailId(null)}>
        {detail && (
          <>
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="工单">{detail.woNo}</Descriptions.Item>
              <Descriptions.Item label="状态">{detail.status === 'AUDITED' ? '已审核' : '草稿'}</Descriptions.Item>
              <Descriptions.Item label="审核时间">{detail.auditTime ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="ERP回传">{detail.erpPushed === 1 ? '已回传' : '未回传'}</Descriptions.Item>
            </Descriptions>
            <Table rowKey="id" size="small" style={{ marginTop: 12 }} columns={itemColumns} dataSource={detail.items ?? []} pagination={false} />
          </>
        )}
      </Drawer>
    </>
  )
}
