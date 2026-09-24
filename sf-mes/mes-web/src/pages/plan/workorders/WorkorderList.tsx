import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Col,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  StopOutlined,
} from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import type { WoStep, WoMaterial } from '@/api/plan'
import {
  WO_STATUS,
  STEP_STATUS,
  closeWorkorder,
  createWorkorder,
  deleteWorkorder,
  finishWorkorder,
  getWorkorderDetail,
  getWorkorders,
  releaseWorkorder,
  updateWorkorder,
  type Workorder,
  type WorkorderForm as WorkorderFormValues,
} from '@/api/plan'
import { getMaterials } from '@/api/basedata'

/** 生产工单管理页：列表 + 状态流转 + 详情（工序/用料） */
export default function WorkorderList() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm<Record<string, unknown>>()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Workorder | null>(null)
  const [detailId, setDetailId] = useState<number | null>(null)

  const { data: materials } = useQuery({
    queryKey: ['basedata', 'materials', { pageSize: 200 }],
    queryFn: () => getMaterials({ pageNum: 1, pageSize: 200 }),
  })

  const { data, isLoading } = useQuery({
    queryKey: ['plan', 'workorders', keyword, status],
    queryFn: () => getWorkorders({ pageNum: 1, pageSize: 20, keyword: keyword || undefined, status }),
  })

  const { data: detail } = useQuery({
    queryKey: ['plan', 'workorder', detailId],
    queryFn: () => getWorkorderDetail(detailId!),
    enabled: detailId != null,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['plan', 'workorders'] })
    queryClient.invalidateQueries({ queryKey: ['plan', 'workorder'] })
  }

  const saveMutation = useMutation({
    mutationFn: async (values: Record<string, unknown>) => {
      const payload: WorkorderFormValues = {
        materialId: values.materialId as number,
        qty: values.qty as number,
        bomId: (values.bomId as number) ?? null,
        routingId: (values.routingId as number) ?? null,
        warehouseId: (values.warehouseId as number) ?? null,
        snMode: (values.snMode as string) ?? 'BATCH',
        remark: values.remark as string,
      }
      if (editing) await updateWorkorder(editing.id, payload)
      else await createWorkorder(payload)
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '创建成功（仅草稿可编辑）')
      setModalOpen(false)
      invalidate()
    },
  })

  const actionMutation = useMutation({
    mutationFn: async ({ id, action }: { id: number; action: 'release' | 'finish' | 'close' }) => {
      if (action === 'release') await releaseWorkorder(id)
      else if (action === 'finish') await finishWorkorder(id)
      else await closeWorkorder(id)
    },
    onSuccess: () => {
      message.success('操作成功')
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteWorkorder(id),
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const columns: ColumnsType<Workorder> = [
    { title: '工单号', dataIndex: 'woNo', width: 160 },
    { title: '产品', dataIndex: 'materialName', ellipsis: true, render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}` },
    { title: '计划数量', dataIndex: 'qty', width: 100, align: 'right' },
    { title: '完工/报废', width: 120, align: 'right', render: (_, r) => `${r.finishQty} / ${r.scrapQty}` },
    { title: 'BOM版本', dataIndex: 'bomVersion', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <StatusTag value={WO_STATUS[s] ?? s} semantic={s === 'FINISHED' ? 'success' : s === 'PRODUCING' ? 'processing' : s === 'CLOSED' ? 'default' : 'warning'} />,
    },
    { title: '计划开工', dataIndex: 'planStartDate', width: 110 },
    { title: '来源', dataIndex: 'sourceNo', width: 120, render: (v) => v ?? '手工' },
    {
      title: '操作',
      width: 260,
      render: (_, r) => (
        <Space size={4}>
          <Button size="small" type="link" onClick={() => setDetailId(r.id)}>详情</Button>
          {r.status === 'DRAFT' && (
            <>
              <HasPerm code="plan:workorder:edit">
                <Button size="small" type="link" icon={<EditOutlined />} onClick={() => { setEditing(r); form.setFieldsValue({ materialId: r.materialId, qty: r.qty, snMode: r.snMode, remark: r.remark ?? undefined }); setModalOpen(true) }}>编辑</Button>
              </HasPerm>
              <HasPerm code="plan:workorder:release">
                <Popconfirm title="下达后将展开 BOM 用料与工艺工序，确认？" onConfirm={() => actionMutation.mutate({ id: r.id, action: 'release' })}>
                  <Button size="small" type="link" icon={<PlayCircleOutlined />}>下达</Button>
                </Popconfirm>
              </HasPerm>
              <HasPerm code="plan:workorder:delete">
                <Popconfirm title="确认删除该草稿工单？" onConfirm={() => deleteMutation.mutate(r.id)}>
                  <Button size="small" type="link" danger icon={<DeleteOutlined />}>删除</Button>
                </Popconfirm>
              </HasPerm>
            </>
          )}
          {(r.status === 'RELEASED' || r.status === 'PRODUCING') && (
            <>
              <HasPerm code="plan:workorder:finish">
                <Button size="small" type="link" icon={<CheckCircleOutlined />} onClick={() => actionMutation.mutate({ id: r.id, action: 'finish' })}>完工</Button>
              </HasPerm>
              <HasPerm code="plan:workorder:close">
                <Popconfirm title="确认关闭（结案）该工单？" onConfirm={() => actionMutation.mutate({ id: r.id, action: 'close' })}>
                  <Button size="small" type="link" icon={<StopOutlined />}>关闭</Button>
                </Popconfirm>
              </HasPerm>
            </>
          )}
          {r.status === 'FINISHED' && (
            <HasPerm code="plan:workorder:close">
              <Popconfirm title="确认关闭（结案）该工单？" onConfirm={() => actionMutation.mutate({ id: r.id, action: 'close' })}>
                <Button size="small" type="link" icon={<StopOutlined />}>关闭</Button>
              </Popconfirm>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  const stepColumns: ColumnsType<WoStep> = [
    { title: '序号', dataIndex: 'seqNo', width: 60 },
    { title: '工序', dataIndex: 'operationName' },
    { title: '工作中心', dataIndex: 'workcenterName', render: (v) => v ?? '-' },
    { title: '计划数量', dataIndex: 'planQty', align: 'right' },
    { title: '合格', dataIndex: 'finishQty', align: 'right' },
    { title: '报废', dataIndex: 'scrapQty', align: 'right' },
    { title: '状态', dataIndex: 'status', render: (s: string) => <StatusTag value={STEP_STATUS[s] ?? s} semantic={s === 'FINISHED' ? 'success' : s === 'PROCESSING' ? 'processing' : 'default'} /> },
    { title: '派工对象', dataIndex: 'assigneeName', render: (v) => v ?? '-' },
  ]

  const materialColumns: ColumnsType<WoMaterial> = [
    { title: '物料', render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}` },
    { title: '需求数量', dataIndex: 'reqQty', align: 'right' },
    { title: '已领数量', dataIndex: 'issuedQty', align: 'right' },
  ]

  return (
    <>
      <PageHeader
        title="生产工单"
        subtitle="工单生命周期：草稿 → 下达 → 生产中 → 完工 → 关闭；下达时按 BOM 展开用料、按工艺路线生成工序"
        extra={
          <HasPerm code="plan:workorder:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); form.resetFields(); setModalOpen(true) }}>新建工单</Button>
          </HasPerm>
        }
      />
      <Table<Workorder>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Space>
            <Input.Search placeholder="工单号/来源单号" allowClear style={{ width: 220 }} onSearch={setKeyword} />
            <Select
              allowClear
              placeholder="状态"
              style={{ width: 120 }}
              onChange={(v) => setStatus(v)}
              options={Object.entries(WO_STATUS).map(([value, label]) => ({ value, label }))}
            />
          </Space>
        )}
      />

      <Modal
        title={editing ? '编辑工单（草稿）' : '新建工单'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => saveMutation.mutate(v)}>
          <Form.Item name="materialId" label="产品物料" rules={[{ required: true, message: '请选择产品物料' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择成品物料"
              options={(materials?.records ?? []).map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))}
            />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="qty" label="计划数量" rules={[{ required: true, message: '请输入数量' }]}>
                <InputNumber min={0.0001} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="snMode" label="追踪模式" initialValue="BATCH">
                <Select options={[{ value: 'BATCH', label: '批次' }, { value: 'SN', label: '单品序列号' }]} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={200} />
          </Form.Item>
          <div style={{ color: '#999', fontSize: 12 }}>
            BOM 与工艺路线默认取物料当前有效版本；ERP 下推工单来源标记为 ERP_ORDER。
          </div>
        </Form>
      </Modal>

      <Drawer title={`工单详情：${detail?.woNo ?? ''}`} width={760} open={detailId != null} onClose={() => setDetailId(null)}>
        {detail && (
          <>
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="产品">{`${detail.materialCode ?? ''} ${detail.materialName ?? ''}`}</Descriptions.Item>
              <Descriptions.Item label="状态">{WO_STATUS[detail.status]}</Descriptions.Item>
              <Descriptions.Item label="计划 / 完工 / 报废">{`${detail.qty} / ${detail.finishQty} / ${detail.scrapQty}`}</Descriptions.Item>
              <Descriptions.Item label="来源">{detail.sourceNo ?? '手工'}</Descriptions.Item>
            </Descriptions>
            <Tabs
              style={{ marginTop: 16 }}
              items={[
                { key: 'steps', label: `工序任务（${detail.steps?.length ?? 0}）`, children: <Table rowKey="id" size="small" columns={stepColumns} dataSource={detail.steps ?? []} pagination={false} /> },
                { key: 'materials', label: `用料需求（${detail.materials?.length ?? 0}）`, children: <Table rowKey="id" size="small" columns={materialColumns} dataSource={detail.materials ?? []} pagination={false} /> },
              ]}
            />
          </>
        )}
      </Drawer>
    </>
  )
}
