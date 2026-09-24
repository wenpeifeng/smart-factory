import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Descriptions, Drawer, Form, Input, InputNumber, Modal, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import {
  INSPECT_TYPE,
  createInspect,
  fillInspectValues,
  getInspectDetail,
  getInspects,
  getTemplates,
  submitInspect,
  type QcInspect,
  type QcInspectItem,
} from '@/api/quality'
import { getMaterials } from '@/api/basedata'

/** 检验单页：创建（选模板带出项目）→ 录入实测值 → 提交（汇总结论+回传 ERP） */
export default function InspectList() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm()
  const [keyword, setKeyword] = useState('')
  const [inspectType, setInspectType] = useState<string | undefined>()
  const [statusFilter, setStatusFilter] = useState<number | undefined>()
  const [modalOpen, setModalOpen] = useState(false)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [fills, setFills] = useState<Record<number, string>>({})

  const { data, isLoading } = useQuery({
    queryKey: ['quality', 'inspects', keyword, inspectType, statusFilter],
    queryFn: () => getInspects({ pageNum: 1, pageSize: 20, keyword: keyword || undefined, inspectType, status: statusFilter }),
  })
  const { data: templates } = useQuery({
    queryKey: ['quality', 'templates', 'for-inspect'],
    queryFn: () => getTemplates({ pageNum: 1, pageSize: 100, inspectType }),
  })
  const { data: materials } = useQuery({ queryKey: ['basedata', 'materials', 'for-qc'], queryFn: () => getMaterials({ pageNum: 1, pageSize: 200 }) })
  const { data: detail, refetch: refetchDetail } = useQuery({
    queryKey: ['quality', 'inspect', detailId],
    queryFn: () => getInspectDetail(detailId!),
    enabled: detailId != null,
  })

  const createMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) =>
      createInspect({
        inspectType: values.inspectType as string,
        sourceNo: values.sourceNo as string,
        templateId: values.templateId as number,
        materialId: values.materialId as number,
        batchNo: values.batchNo as string,
        sampleQty: values.sampleQty as number,
        inspectorName: values.inspectorName as string,
      }),
    onSuccess: () => {
      message.success('检验单已创建（草稿），请在详情中录入实测值')
      setModalOpen(false)
      queryClient.invalidateQueries({ queryKey: ['quality', 'inspects'] })
    },
  })

  const fillMutation = useMutation({
    mutationFn: (id: number) =>
      fillInspectValues(id, Object.entries(fills).map(([itemId, actualValue]) => ({ itemId: Number(itemId), actualValue }))),
    onSuccess: () => {
      message.success('实测值已保存')
      setFills({})
      refetchDetail()
    },
  })

  const submitMutation = useMutation({
    mutationFn: (id: number) => submitInspect(id),
    onSuccess: () => {
      message.success('检验单已提交，结论生成并回传 ERP')
      refetchDetail()
      queryClient.invalidateQueries({ queryKey: ['quality', 'inspects'] })
    },
  })

  const columns: ColumnsType<QcInspect> = [
    { title: '检验单号', dataIndex: 'inspectNo', width: 160 },
    { title: '类型', dataIndex: 'inspectType', width: 90, render: (v: string) => INSPECT_TYPE[v] ?? v },
    { title: '物料', render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}` },
    { title: '抽检数', dataIndex: 'sampleQty', width: 90, align: 'right' },
    {
      title: '结论',
      dataIndex: 'conclusion',
      width: 90,
      render: (v: number | null) => (v == null ? '-' : <StatusTag value={v === 1 ? '合格' : '不合格'} semantic={v === 1 ? 'success' : 'error'} />),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) => <StatusTag value={v === 1 ? '已提交' : '草稿'} semantic={v === 1 ? 'success' : 'processing'} />,
    },
    { title: '检验员', dataIndex: 'inspectorName', width: 100 },
    { title: '检验日期', dataIndex: 'inspectDate', width: 110 },
    {
      title: '操作',
      render: (_, r) => <Button size="small" type="link" onClick={() => { setDetailId(r.id); setFills({}) }}>详情</Button>,
    },
  ]

  const itemColumns: ColumnsType<QcInspectItem & { filled?: string }> = [
    { title: '项目', dataIndex: 'itemName' },
    { title: '标准值', dataIndex: 'standardValue', render: (v) => v ?? '-' },
    { title: '范围', render: (_, r) => `${r.lowerLimit ?? '-∞'} ~ ${r.upperLimit ?? '+∞'}` },
    {
      title: '实测值',
      dataIndex: 'actualValue',
      render: (_, r) =>
        detail?.status === 0 ? (
          <Input
            size="small"
            style={{ width: 120 }}
            defaultValue={r.actualValue ?? undefined}
            onChange={(e) => setFills((prev) => ({ ...prev, [r.id]: e.target.value }))}
          />
        ) : (
          r.actualValue ?? '-'
        ),
    },
    {
      title: '结论',
      dataIndex: 'lineResult',
      width: 90,
      render: (v: number | null) =>
        v == null ? '-' : <StatusTag value={v === 1 ? '合格' : '不合格'} semantic={v === 1 ? 'success' : 'error'} />,
    },
  ]

  return (
    <>
      <PageHeader
        title="检验单"
        subtitle="IQC/IPQC/FQC 三类检验共用：创建 → 录入实测值（上下限自动判定）→ 提交（汇总结论并回传 ERP）"
        extra={
          <HasPerm code="quality:inspect:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true) }}>新建检验单</Button>
          </HasPerm>
        }
      />
      <Table<QcInspect>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Space>
            <Input.Search placeholder="检验单号/来源单号" allowClear style={{ width: 220 }} onSearch={setKeyword} />
            <Select allowClear placeholder="类型" style={{ width: 110 }} onChange={setInspectType} options={Object.entries(INSPECT_TYPE).map(([value, label]) => ({ value, label }))} />
            <Select allowClear placeholder="状态" style={{ width: 100 }} onChange={(v) => setStatusFilter(v)} options={[{ value: 0, label: '草稿' }, { value: 1, label: '已提交' }]} />
          </Space>
        )}
      />

      <Modal
        title="新建检验单"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => createMutation.mutate(v)}>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="inspectType" label="检验类型" initialValue="IQC" rules={[{ required: true }]}>
              <Select style={{ width: 130 }} options={Object.entries(INSPECT_TYPE).map(([value, label]) => ({ value, label }))} />
            </Form.Item>
            <Form.Item name="templateId" label="检验模板" rules={[{ required: true, message: '请选择模板' }]}>
              <Select style={{ width: 200 }} showSearch optionFilterProp="label" options={(templates?.records ?? []).filter((t) => t.status === 1).map((t) => ({ value: t.id, label: t.templateName }))} />
            </Form.Item>
          </Space>
          <Form.Item name="materialId" label="物料" rules={[{ required: true, message: '请选择物料' }]}>
            <Select showSearch optionFilterProp="label" options={(materials?.records ?? []).map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))} />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="sourceNo" label="来源单号">
              <Input placeholder="到货单/工单号" />
            </Form.Item>
            <Form.Item name="batchNo" label="批次号">
              <Input />
            </Form.Item>
            <Form.Item name="sampleQty" label="抽检数量" initialValue={1} rules={[{ required: true }]}>
              <InputNumber min={0.0001} />
            </Form.Item>
            <Form.Item name="inspectorName" label="检验员">
              <Input />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Drawer
        title={`检验单：${detail?.inspectNo ?? ''}`}
        width={760}
        open={detailId != null}
        onClose={() => setDetailId(null)}
        extra={
          detail?.status === 0 && (
            <Space>
              <HasPerm code="quality:inspect:fill">
                <Button size="small" onClick={() => fillMutation.mutate(detail.id)} loading={fillMutation.isPending}>保存实测值</Button>
              </HasPerm>
              <HasPerm code="quality:inspect:submit">
                <Button size="small" type="primary" onClick={() => submitMutation.mutate(detail.id)} loading={submitMutation.isPending}>提交</Button>
              </HasPerm>
            </Space>
          )
        }
      >
        {detail && (
          <>
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="物料">{`${detail.materialCode ?? ''} ${detail.materialName ?? ''}`}</Descriptions.Item>
              <Descriptions.Item label="类型">{INSPECT_TYPE[detail.inspectType] ?? detail.inspectType}</Descriptions.Item>
              <Descriptions.Item label="抽检/合格/不合格">{`${detail.sampleQty} / ${detail.qualifiedQty ?? '-'} / ${detail.unqualifiedQty ?? '-'}`}</Descriptions.Item>
              <Descriptions.Item label="结论">{detail.conclusion == null ? '-' : detail.conclusion === 1 ? '合格' : '不合格'}</Descriptions.Item>
            </Descriptions>
            <Table rowKey="id" size="small" style={{ marginTop: 12 }} columns={itemColumns} dataSource={detail.items ?? []} pagination={false} />
          </>
        )}
      </Drawer>
    </>
  )
}
