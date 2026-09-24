import HasPerm from '@/components/HasPerm'
import StatusTag from '@/components/StatusTag'
import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import { ExportOutlined, ImportOutlined, ScissorOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  SERIAL_STATUS_MAP,
  fetchMaterialOptions,
  fetchSerialPage,
  fetchWarehouseOptions,
  registerSerials,
  scrapSerials,
  writeOffSerials,
  type MaterialOption,
  type SerialRow,
} from '@/api/inventory'

const { Title, Text } = Typography

interface RegisterFormValues {
  materialId: number
  warehouseId: number
  batchNo?: string
  sourceNo?: string
  serialNosText: string
}

interface WriteOffFormValues {
  outNo?: string
  serialNosText: string
}

/**
 * 序列号管理页：入库登记（批量粘贴/逐条）→ 出库核销 → 全程正反查追溯。
 */
export default function SerialList() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<{ serialNo?: string; materialId?: number; status?: string; docNo?: string }>({})
  const [pagination, setPagination] = useState({ pageNum: 1, pageSize: 10 })
  const [registerOpen, setRegisterOpen] = useState(false)
  const [writeOffOpen, setWriteOffOpen] = useState(false)
  const [registerForm] = Form.useForm<RegisterFormValues>()
  const [writeOffForm] = Form.useForm<WriteOffFormValues>()
  const [detail, setDetail] = useState<SerialRow | null>(null)

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['inventory'] })

  const { data: warehouses } = useQuery({
    queryKey: ['inventory', 'warehouseOptions'],
    queryFn: fetchWarehouseOptions,
    staleTime: 5 * 60 * 1000,
  })
  const whOptions = (warehouses ?? []).map((w) => ({ label: `${w.code} ${w.name}`, value: w.id }))

  const { data: materials } = useQuery({
    queryKey: ['inventory', 'materialOptions'],
    queryFn: () => fetchMaterialOptions(),
    staleTime: 5 * 60 * 1000,
  })
  const matOptions = (materials ?? []).map((m: MaterialOption) => ({
    label: `${m.code} ${m.name}`,
    value: m.id,
  }))

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['inventory', 'serials', filters, pagination],
    queryFn: () => fetchSerialPage({ ...pagination, ...filters }),
    placeholderData: keepPreviousData,
  })

  const parseSerialNos = (text?: string) =>
    (text ?? '')
      .split(/[\n,，;；\s]+/)
      .map((s) => s.trim())
      .filter(Boolean)

  const registerMutation = useMutation({
    mutationFn: (values: RegisterFormValues) =>
      registerSerials({
        materialId: values.materialId,
        warehouseId: values.warehouseId,
        batchNo: values.batchNo?.trim() || undefined,
        sourceNo: values.sourceNo?.trim() || undefined,
        serialNos: parseSerialNos(values.serialNosText),
      }),
    onSuccess: (r) => {
      message.success(`登记成功 ${r.registered} 条序列号（IN_STOCK）`)
      setRegisterOpen(false)
      registerForm.resetFields()
      invalidate()
    },
  })

  const writeOffMutation = useMutation({
    mutationFn: (values: WriteOffFormValues) =>
      writeOffSerials({
        outNo: values.outNo?.trim() || undefined,
        serialNos: parseSerialNos(values.serialNosText),
      }),
    onSuccess: (r) => {
      message.success(`核销成功 ${r.writtenOff} 条序列号（OUT）`)
      setWriteOffOpen(false)
      writeOffForm.resetFields()
      invalidate()
    },
  })

  const scrapMutation = useMutation({
    mutationFn: (row: SerialRow) => scrapSerials({ serialNos: [row.serialNo] }),
    onSuccess: (r) => {
      message.success(`报废 ${r.scrapped} 条`)
      invalidate()
    },
  })

  const columns: ColumnsType<SerialRow> = [
    { title: '序列号', dataIndex: 'serialNo', width: 170, render: (v: string) => <span className="font-mono">{v}</span> },
    { title: '物料编码', dataIndex: 'materialCode', width: 110, render: (v: string | null) => v ?? '-' },
    { title: '物料名称', dataIndex: 'materialName', ellipsis: true, render: (v: string | null) => v ?? '-' },
    { title: '仓库', dataIndex: 'warehouseName', width: 110, render: (v: string | null) => v ?? '-' },
    { title: '批次', dataIndex: 'batchNo', width: 110, render: (v: string) => (v ? <Tag>{v}</Tag> : '-') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => <StatusTag value={SERIAL_STATUS_MAP[v] ?? v} />,
    },
    { title: '入库来源单号', dataIndex: 'sourceNo', width: 140, render: (v: string | null) => v ?? '-' },
    { title: '出库单号', dataIndex: 'outNo', width: 140, render: (v: string | null) => v ?? '-' },
    { title: '登记时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_, record) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => setDetail(record)}>
            追溯
          </Button>
          {record.status === 'IN_STOCK' && (
            <HasPerm code="inventory:wms:serial:audit">
              <Popconfirm
                title="报废该序列号？"
                description="IN_STOCK → SCRAP，报废后不可核销"
                onConfirm={() => scrapMutation.mutate(record)}
              >
                <Button type="link" size="small" danger icon={<ScissorOutlined />}>
                  报废
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        序列号管理
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search
            style={{ width: 220 }}
            placeholder="序列号（精确/前缀正查）"
            allowClear
            onSearch={(v) => {
              setFilters((f) => ({ ...f, serialNo: v || undefined }))
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <Select
            style={{ width: 220 }}
            placeholder="物料（反查）"
            allowClear
            showSearch
            optionFilterProp="label"
            options={matOptions}
            value={filters.materialId}
            onChange={(v) => {
              setFilters((f) => ({ ...f, materialId: v }))
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <Select
            style={{ width: 110 }}
            placeholder="状态"
            allowClear
            options={Object.entries(SERIAL_STATUS_MAP).map(([value, label]) => ({ value, label }))}
            value={filters.status}
            onChange={(v) => {
              setFilters((f) => ({ ...f, status: v }))
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <Input.Search
            style={{ width: 200 }}
            placeholder="来源/出库单号"
            allowClear
            onSearch={(v) => {
              setFilters((f) => ({ ...f, docNo: v || undefined }))
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <HasPerm code="inventory:wms:serial:edit">
            <Button type="primary" icon={<ImportOutlined />} onClick={() => setRegisterOpen(true)}>
              入库登记
            </Button>
          </HasPerm>
          <HasPerm code="inventory:wms:serial:audit">
            <Button danger icon={<ExportOutlined />} onClick={() => setWriteOffOpen(true)}>
              出库核销
            </Button>
          </HasPerm>
        </Space>

        <Table<SerialRow>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          scroll={{ x: 1280 }}
          locale={{ emptyText: '暂无序列号（入库登记后在此追溯全生命周期）' }}
          pagination={{
            total: data?.total ?? 0,
            current: data?.pageNum ?? 1,
            pageSize: data?.pageSize ?? 10,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (pageNum, pageSize) => setPagination({ pageNum, pageSize }),
          }}
        />
      </Card>

      {/* 入库登记：批量粘贴/逐条共用 */}
      <Modal
        title="序列号入库登记"
        open={registerOpen}
        onCancel={() => setRegisterOpen(false)}
        onOk={() => registerForm.submit()}
        confirmLoading={registerMutation.isPending}
        okText="登记"
        destroyOnClose
        width={560}
      >
        <Form<RegisterFormValues> form={registerForm} layout="vertical" onFinish={(values) => registerMutation.mutate(values)}>
          <Form.Item name="materialId" label="物料" rules={[{ required: true, message: '请选择物料' }]}>
            <Select placeholder="选择物料" options={matOptions} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="warehouseId" label="入库仓库" rules={[{ required: true, message: '请选择仓库' }]}>
            <Select placeholder="选择仓库" options={whOptions} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="batchNo" label="批次号（可选）">
            <Input maxLength={50} placeholder="批次物料可关联批次" />
          </Form.Item>
          <Form.Item name="sourceNo" label="入库来源单号（可选）">
            <Input maxLength={40} placeholder="如采购入库单号 RK..." />
          </Form.Item>
          <Form.Item
            name="serialNosText"
            label="序列号列表（换行/逗号分隔，一次最多 500 条）"
            rules={[
              { required: true, message: '请录入序列号' },
              {
                validator: (_, v: string | undefined) =>
                  parseSerialNos(v).length > 500
                    ? Promise.reject(new Error('单次不超过 500 条'))
                    : parseSerialNos(v).length === 0
                      ? Promise.reject(new Error('至少录入一条序列号'))
                      : Promise.resolve(),
              },
            ]}
          >
            <Input.TextArea rows={6} placeholder={'SN2026090500001\nSN2026090500002\nSN2026090500003'} />
          </Form.Item>
          <p className="text-xs text-gray-500">登记只写追溯台账（IN_STOCK），库存数量账由对应入库单审核过账。</p>
        </Form>
      </Modal>

      {/* 出库核销 */}
      <Modal
        title="序列号出库核销"
        open={writeOffOpen}
        onCancel={() => setWriteOffOpen(false)}
        onOk={() => writeOffForm.submit()}
        confirmLoading={writeOffMutation.isPending}
        okText="核销"
        okButtonProps={{ danger: true }}
        destroyOnClose
      >
        <Form<WriteOffFormValues> form={writeOffForm} layout="vertical" onFinish={(values) => writeOffMutation.mutate(values)}>
          <Form.Item name="outNo" label="出库单号（可选，核销时记录）">
            <Input maxLength={40} placeholder="如销售出库单号 XS..." />
          </Form.Item>
          <Form.Item
            name="serialNosText"
            label="序列号列表（换行/逗号分隔；需全部为在库状态）"
            rules={[
              { required: true, message: '请录入序列号' },
              {
                validator: (_, v: string | undefined) =>
                  parseSerialNos(v).length === 0 ? Promise.reject(new Error('至少录入一条')) : Promise.resolve(),
              },
            ]}
          >
            <Input.TextArea rows={5} placeholder="扫码或粘贴序列号" />
          </Form.Item>
          <p className="text-xs text-gray-500">任一序列号不存在或非在库状态则整体回滚；核销后状态=已出库。</p>
        </Form>
      </Modal>

      {/* 单序列号追溯 */}
      <Drawer title="序列号追溯" width={440} open={detail != null} onClose={() => setDetail(null)}>
        {detail && (
          <Descriptions size="small" column={1} bordered>
            <Descriptions.Item label="序列号">
              <span className="font-mono">{detail.serialNo}</span>
            </Descriptions.Item>
            <Descriptions.Item label="物料">
              {detail.materialCode ?? '-'} {detail.materialName ?? ''}
            </Descriptions.Item>
            <Descriptions.Item label="仓库">{detail.warehouseName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="批次">{detail.batchNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <StatusTag value={SERIAL_STATUS_MAP[detail.status] ?? detail.status} />
            </Descriptions.Item>
            <Descriptions.Item label="入库来源单号">{detail.sourceNo ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="出库单号">{detail.outNo ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="登记时间">{detail.createTime}</Descriptions.Item>
            <Descriptions.Item label="最后变动">{detail.updateTime}</Descriptions.Item>
            <Descriptions.Item label="备注">{detail.remark ?? '-'}</Descriptions.Item>
          </Descriptions>
        )}
        <p className="mt-3 text-xs text-gray-500">
          <Text type="secondary">正查=按序列号定位单据（本页）；反查=按物料筛出全部序列号（列表物料筛选）。</Text>
        </p>
      </Drawer>
    </div>
  )
}
