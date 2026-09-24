import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  rejectApi,
  type QcRejectRow,
  type QcRejectSavePayload,
  type RejectSource,
  type QcRejectPageParams,
} from '@/api/qc'
import { DecisionTag, DECISION_OPTIONS, fmtQty } from '../shared'

const { Title } = Typography

/**
 * 不合格品处置页：列表 + 新增/修改弹窗（来源检验单下拉带出剩余可处置数量）+ 详情 Drawer。
 */
export default function RejectList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [params, setParams] = useState<QcRejectPageParams>({ pageNum: 1, pageSize: 10 })
  const [editing, setEditing] = useState<QcRejectRow | null>(null)
  const [open, setOpen] = useState(false)
  const [detail, setDetail] = useState<QcRejectRow | null>(null)
  const [form] = Form.useForm<QcRejectSavePayload>()

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['qc', 'rejects', params],
    queryFn: () => rejectApi.page(params),
  })
  const { data: sources } = useQuery({
    queryKey: ['qc', 'rejects', 'sources'],
    queryFn: rejectApi.sources,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['qc', 'rejects'] })
  }

  const saveMutation = useMutation({
    mutationFn: async (values: QcRejectSavePayload) => {
      if (editing) {
        await rejectApi.update(editing.id, values)
      } else {
        await rejectApi.create(values)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功')
      setOpen(false)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: rejectApi.remove,
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ decision: 2 })
    setOpen(true)
  }

  const openEdit = (row: QcRejectRow) => {
    setEditing(row)
    form.resetFields()
    form.setFieldsValue({
      inspectId: row.inspectId,
      qty: row.qty,
      decision: row.decision,
      disposeRemark: row.disposeRemark ?? undefined,
      approver: row.approver ?? undefined,
    })
    setOpen(true)
  }

  /** 当前选中来源检验单（新增时用于数量上限） */
  const selectedSource = Form.useWatch('inspectId', form)
  const sourceInfo: RejectSource | undefined = (sources ?? []).find((s) => s.inspectId === selectedSource)

  const columns: ColumnsType<QcRejectRow> = [
    { title: '处置单号', dataIndex: 'rejectNo', width: 150 },
    { title: '来源检验单号', dataIndex: 'inspectNo', width: 150 },
    {
      title: '物料',
      dataIndex: 'materialName',
      width: 200,
      ellipsis: true,
      render: (v, record) => (
        <>
          {record.materialCode} {v}
          {record.spec ? `（${record.spec}）` : ''}
        </>
      ),
    },
    { title: '处置数量', dataIndex: 'qty', width: 100, align: 'right', render: fmtQty },
    { title: '处置决定', dataIndex: 'decision', width: 100, render: (d) => <DecisionTag decision={d} /> },
    { title: '处置说明', dataIndex: 'disposeRemark', ellipsis: true, render: (v) => v ?? '-' },
    { title: '审批人', dataIndex: 'approver', width: 90, render: (v) => v ?? '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            onClick={() => rejectApi.get(record.id).then((d) => setDetail(d))}
          >
            详情
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该处置单？" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        不合格品处置
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Input.Search
            placeholder="处置单号 / 来源检验单号模糊搜索"
            style={{ width: 260 }}
            allowClear
            onSearch={(v) => setParams((p) => ({ ...p, pageNum: 1, keyword: v || undefined }))}
          />
          <Select
            placeholder="处置决定"
            style={{ width: 130 }}
            allowClear
            options={DECISION_OPTIONS}
            onChange={(v) => setParams((p) => ({ ...p, pageNum: 1, decision: v }))}
          />
          <Button type="primary" onClick={openCreate} disabled={(sources ?? []).length === 0}>
            新增处置单
          </Button>
        </Space>

        <Table<QcRejectRow>
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          scroll={{ x: 1300 }}
          pagination={{
            total: data?.total ?? 0,
            current: data?.pageNum ?? params.pageNum,
            pageSize: data?.pageSize ?? params.pageSize,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => setParams({ pageNum: p, pageSize: s }),
          }}
        />
      </Card>

      {/* 新增 / 修改弹窗 */}
      <Modal
        title={editing ? `修改处置单 - ${editing.rejectNo}` : '新增不合格品处置单'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
        width={560}
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Form.Item
            name="inspectId"
            label="来源检验单（已检验且不合格数>0）"
            rules={[{ required: true, message: '请选择来源检验单' }]}
            extra={
              editing
                ? '修改时不允许变更来源检验单'
                : sourceInfo
                  ? `不合格 ${fmtQty(sourceInfo.unqualifiedQty)}，已处置 ${fmtQty(sourceInfo.disposedQty)}，可处置 ${fmtQty(sourceInfo.rejectableQty)}`
                  : '选择后显示剩余可处置数量'
            }
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择检验单"
              disabled={!!editing}
              options={(sources ?? []).map((s) => ({
                value: s.inspectId,
                label: `${s.inspectNo}｜${s.materialCode ?? ''} ${s.materialName ?? ''}｜可处置 ${s.rejectableQty}`,
              }))}
            />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="qty"
              label="处置数量"
              rules={[{ required: true, message: '请输入处置数量' }]}
              style={{ width: 200 }}
            >
              <InputNumber
                min={0.0001}
                max={editing ? undefined : sourceInfo?.rejectableQty}
                style={{ width: '100%' }}
              />
            </Form.Item>
            <Form.Item
              name="decision"
              label="处置决定"
              rules={[{ required: true, message: '请选择处置决定' }]}
              style={{ width: 200 }}
            >
              <Select options={DECISION_OPTIONS} />
            </Form.Item>
          </Space>
          <Form.Item name="approver" label="审批人">
            <Input placeholder="审批人" maxLength={50} />
          </Form.Item>
          <Form.Item name="disposeRemark" label="处置说明">
            <Input.TextArea rows={3} maxLength={500} placeholder="处置说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 详情 Drawer */}
      <Drawer
        title={`处置单详情 - ${detail?.rejectNo ?? ''}`}
        open={!!detail}
        onClose={() => setDetail(null)}
        width={560}
        destroyOnClose
      >
        {detail ? (
          <Descriptions size="small" bordered column={1}>
            <Descriptions.Item label="处置单号">{detail.rejectNo}</Descriptions.Item>
            <Descriptions.Item label="来源检验单">{detail.inspectNo}</Descriptions.Item>
            <Descriptions.Item label="物料">
              {detail.materialCode} {detail.materialName}
              {detail.spec ? `（${detail.spec}）` : ''}
            </Descriptions.Item>
            <Descriptions.Item label="处置数量">{fmtQty(detail.qty)}</Descriptions.Item>
            <Descriptions.Item label="处置决定">
              <DecisionTag decision={detail.decision} />
            </Descriptions.Item>
            <Descriptions.Item label="处置说明">{detail.disposeRemark ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="审批人">{detail.approver ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{detail.createTime}</Descriptions.Item>
          </Descriptions>
        ) : null}
      </Drawer>
    </div>
  )
}
