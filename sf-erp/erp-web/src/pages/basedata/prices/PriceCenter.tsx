import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Col,
  DatePicker,
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
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import {
  applyPriceSpecial,
  approvePriceSpecial,
  createPriceContract,
  createPriceLevel,
  createPriceQty,
  createPriceRecord,
  deletePriceContract,
  deletePriceLevel,
  deletePriceQty,
  deletePriceRecord,
  deletePriceSpecial,
  getEnabledCustomers,
  getEnabledMaterials,
  getEnabledSuppliers,
  getPriceCompare,
  getPriceContracts,
  getPriceLevels,
  getPriceQtys,
  getPriceRecords,
  getPriceSpecials,
  rejectPriceSpecial,
  updatePriceContract,
  updatePriceLevel,
  updatePriceQty,
  type PriceCompareRow,
  type PriceContract,
  type PriceLevel,
  type PriceQty,
  type PriceRecord,
  type PriceSpecial,
} from '@/api/basedata'

const { Title, Text } = Typography

/** 单价/金额显示（最多 4 位小数，与 DECIMAL(18,4) 对齐） */
function fmtPrice(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
}

/** 客户等级选项 */
const LEVEL_OPTIONS = [
  { value: 'A', label: 'A（重点客户）' },
  { value: 'B', label: 'B（普通客户）' },
  { value: 'C', label: 'C（一般客户）' },
]

/** 来源类型选项 */
const SOURCE_TYPE_OPTIONS = [
  { value: 'ORDER', label: '采购订单' },
  { value: 'INSTOCK', label: '采购入库' },
  { value: 'RFQ', label: '询比价定标' },
  { value: 'CONTRACT', label: '合同/手工' },
]

/**
 * 价格中心（V131）：采购价格流水 / 合同价 / 等级价 / 批量价 / 特价审批 五个 Tab。
 * 采购价格流水由扫描器从已审核订单/入库/定标 RFQ 自动沉淀；参照接口见各录单 Modal 的「价格参照」。
 */
export default function PriceCenter() {
  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        价格管理
        <Text type="secondary" style={{ fontSize: 13, marginLeft: 12, fontWeight: 'normal' }}>
          采购成交价自动沉淀（订单/入库/定标）· 销售定价策略：特价 &gt; 等级价 &gt; 批量价 &gt; 成本加成
        </Text>
      </Title>

      <Card size="small">
        <Tabs
          defaultActiveKey="records"
          items={[
            {
              key: 'records',
              label: '采购价格流水',
              children: <RecordTab />,
            },
            {
              key: 'contracts',
              label: '合同价',
              children: <ContractTab />,
            },
            {
              key: 'levels',
              label: '等级价',
              children: <LevelTab />,
            },
            {
              key: 'qtys',
              label: '批量价',
              children: <QtyTab />,
            },
            {
              key: 'special',
              label: '特价审批',
              children: (
                <HasPerm code="sales:priceSpecial:list" fallback={<Text type="secondary">无特价审批查看权限</Text>}>
                  <SpecialTab />
                </HasPerm>
              ),
            },
          ]}
        />
      </Card>
    </div>
  )
}

/* ===================== Tab1：采购价格流水 ===================== */

function RecordTab() {
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [supplierId, setSupplierId] = useState<number | undefined>()
  const [materialId, setMaterialId] = useState<number | undefined>()
  const [sourceType, setSourceType] = useState<string | undefined>()
  const [compareOpen, setCompareOpen] = useState(false)
  const [compareMaterialId, setCompareMaterialId] = useState<number | undefined>()
  const [manualOpen, setManualOpen] = useState(false)

  const suppliers = useEnabledSuppliers()
  const materials = useEnabledMaterials()

  const params = { pageNum, pageSize, supplierId, materialId, sourceType }
  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['price', 'records', params],
    queryFn: () => getPriceRecords(params),
  })
  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['price', 'records'] })

  const deleteMut = useMutation({
    mutationFn: (id: number) => deletePriceRecord(id),
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
  })

  const columns: ColumnsType<PriceRecord> = [
    { title: '日期', dataIndex: 'priceDate', width: 105 },
    { title: '供应商', dataIndex: 'supplierName', width: 150, ellipsis: true },
    {
      title: '物料',
      dataIndex: 'materialCode',
      width: 210,
      ellipsis: true,
      render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}`,
    },
    { title: '单价(不含税)', dataIndex: 'price', width: 110, align: 'right', render: (v: number) => <Text strong>{fmtPrice(v)}</Text> },
    {
      title: '来源',
      dataIndex: 'sourceType',
      width: 100,
      render: (v: string) => (
        <Tag color={v === 'RFQ' ? 'purple' : v === 'INSTOCK' ? 'cyan' : v === 'ORDER' ? 'blue' : 'gold'}>{v}</Tag>
      ),
    },
    { title: '来源单号', dataIndex: 'sourceNo', width: 150 },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_, record) => (
        <HasPerm code="basedata:price:delete">
          <Popconfirm title="确认删除该流水？（删除后扫描器不会重新沉淀）" onConfirm={() => deleteMut.mutate(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </HasPerm>
      ),
    },
  ]

  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          placeholder="供应商"
          style={{ width: 200 }}
          allowClear
          showSearch
          optionFilterProp="label"
          options={suppliers.map((s) => ({ value: s.id, label: `${s.code} ${s.name}` }))}
          onChange={(v) => {
            setPageNum(1)
            setSupplierId(v)
          }}
        />
        <Select
          placeholder="物料"
          style={{ width: 240 }}
          allowClear
          showSearch
          optionFilterProp="label"
          options={materials.map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))}
          onChange={(v) => {
            setPageNum(1)
            setMaterialId(v)
          }}
        />
        <Select
          placeholder="来源类型"
          style={{ width: 140 }}
          allowClear
          options={SOURCE_TYPE_OPTIONS}
          onChange={(v) => {
            setPageNum(1)
            setSourceType(v)
          }}
        />
        <Button
          onClick={() => {
            setCompareMaterialId(materialId)
            setCompareOpen(true)
          }}
          disabled={materialId == null}
        >
          供应商比价
        </Button>
        <HasPerm code="basedata:price:add">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setManualOpen(true)}>
            补录流水
          </Button>
        </HasPerm>
      </Space>

      <Table<PriceRecord>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading || isFetching}
        scroll={{ x: 1100 }}
        pagination={{
          total: data?.total ?? 0,
          current: data?.pageNum ?? 1,
          pageSize: data?.pageSize ?? 10,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPageNum(p)
            setPageSize(s)
          },
        }}
      />

      <CompareDrawer open={compareOpen} materialId={compareMaterialId} onClose={() => setCompareOpen(false)} />
      {manualOpen && (
        <ManualRecordModal
          onClose={() => setManualOpen(false)}
          onSuccess={() => {
            setManualOpen(false)
            invalidate()
          }}
        />
      )}
    </>
  )
}

/** 供应商比价抽屉：多供应商同物料 最低/最高/均价/最近价/合同价 */
function CompareDrawer({ open, materialId, onClose }: { open: boolean; materialId?: number; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['price', 'compare', materialId],
    queryFn: () => getPriceCompare(materialId as number),
    enabled: open && materialId != null,
  })
  const columns: ColumnsType<PriceCompareRow> = [
    { title: '供应商', dataIndex: 'supplierName', width: 170, ellipsis: true },
    { title: '成交次数', dataIndex: 'times', width: 90, align: 'right' },
    { title: '最低价', dataIndex: 'minPrice', width: 100, align: 'right', render: fmtPrice },
    { title: '最高价', dataIndex: 'maxPrice', width: 100, align: 'right', render: fmtPrice },
    { title: '均价', dataIndex: 'avgPrice', width: 100, align: 'right', render: fmtPrice },
    {
      title: '最近价',
      dataIndex: 'lastPrice',
      width: 100,
      align: 'right',
      render: (v: number, r) => (
        <Space size={4}>
          <Text strong>{fmtPrice(v)}</Text>
          {r.lastDate ? <Text type="secondary" style={{ fontSize: 12 }}>{r.lastDate}</Text> : null}
        </Space>
      ),
    },
    {
      title: '合同价',
      dataIndex: 'contractPrice',
      width: 110,
      align: 'right',
      render: (v: number | null) => (v != null ? <Tag color="gold">{fmtPrice(v)}</Tag> : '-'),
    },
  ]
  return (
    <Drawer title="供应商比价（按最近价升序）" width={860} open={open} onClose={onClose}>
      <Table<PriceCompareRow>
        rowKey="supplierId"
        size="small"
        columns={columns}
        dataSource={data ?? []}
        loading={isLoading}
        pagination={false}
      />
    </Drawer>
  )
}

/** 手工补录流水 Modal（历史导入/合同价） */
function ManualRecordModal({ onClose, onSuccess }: { onClose: () => void; onSuccess: () => void }) {
  const [form] = Form.useForm<{ supplierId: number; materialId: number; price: number; priceDate: Dayjs; sourceNo?: string; remark?: string }>()
  const suppliers = useEnabledSuppliers()
  const materials = useEnabledMaterials()
  const saveMut = useMutation({
    mutationFn: (v: { supplierId: number; materialId: number; price: number; priceDate: Dayjs; sourceNo?: string; remark?: string }) =>
      createPriceRecord({
        supplierId: v.supplierId,
        materialId: v.materialId,
        price: v.price,
        priceDate: v.priceDate.format('YYYY-MM-DD'),
        sourceNo: v.sourceNo,
        remark: v.remark,
      }),
    onSuccess: () => {
      message.success('已补录（来源=合同/手工）')
      onSuccess()
    },
  })
  return (
    <Modal
      title="补录价格流水"
      open
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={saveMut.isPending}
      destroyOnClose
    >
      <Form form={form} layout="vertical" initialValues={{ priceDate: dayjs() }} onFinish={(v) => saveMut.mutate(v)}>
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item name="supplierId" label="供应商" rules={[{ required: true, message: '请选择供应商' }]}>
              <Select showSearch optionFilterProp="label" options={suppliers.map((s) => ({ value: s.id, label: `${s.code} ${s.name}` }))} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="materialId" label="物料" rules={[{ required: true, message: '请选择物料' }]}>
              <Select showSearch optionFilterProp="label" options={materials.map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="price" label="单价（不含税）" rules={[{ required: true, message: '请输入单价' }]}>
              <InputNumber min={0.0001} precision={4} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="priceDate" label="价格日期" rules={[{ required: true, message: '请选择日期' }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="sourceNo" label="来源单号">
              <Input maxLength={30} placeholder="合同号等（空=手工录入）" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="remark" label="备注">
              <Input maxLength={200} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  )
}

/* ===================== Tab2：合同价 ===================== */

function ContractTab() {
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [formState, setFormState] = useState<{ open: boolean; editing: PriceContract | null }>({ open: false, editing: null })

  const suppliers = useEnabledSuppliers()
  const params = { pageNum, pageSize }
  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['price', 'contracts', params],
    queryFn: () => getPriceContracts(params),
  })
  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['price', 'contracts'] })
  const deleteMut = useMutation({
    mutationFn: (id: number) => deletePriceContract(id),
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
  })

  const columns: ColumnsType<PriceContract> = [
    { title: '供应商', dataIndex: 'supplierName', width: 170, ellipsis: true },
    {
      title: '物料',
      dataIndex: 'materialCode',
      width: 220,
      ellipsis: true,
      render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}`,
    },
    { title: '合同价(不含税)', dataIndex: 'contractPrice', width: 120, align: 'right', render: (v: number) => <Text strong>{fmtPrice(v)}</Text> },
    { title: '生效起', dataIndex: 'effectiveFrom', width: 105 },
    { title: '生效止', dataIndex: 'effectiveTo', width: 105 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number) => (v === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size={0}>
          <HasPerm code="basedata:price:edit">
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => setFormState({ open: true, editing: record })}>
              编辑
            </Button>
          </HasPerm>
          <HasPerm code="basedata:price:delete">
            <Popconfirm title="确认删除该合同价？" onConfirm={() => deleteMut.mutate(record.id)}>
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </HasPerm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <HasPerm code="basedata:price:add">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setFormState({ open: true, editing: null })}>
            新增合同价
          </Button>
        </HasPerm>
        <Text type="secondary" style={{ fontSize: 12 }}>
          生效期内（启用状态）的合同价在采购录单「价格参照」中优先展示并标注「合同」
        </Text>
      </Space>
      <Table<PriceContract>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading || isFetching}
        scroll={{ x: 1100 }}
        pagination={{
          total: data?.total ?? 0,
          current: data?.pageNum ?? 1,
          pageSize: data?.pageSize ?? 10,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPageNum(p)
            setPageSize(s)
          },
        }}
      />
      {formState.open && (
        <ContractModal
          key={formState.editing?.id ?? 'new'}
          editing={formState.editing}
          suppliers={suppliers}
          onClose={() => setFormState({ open: false, editing: null })}
          onSuccess={() => {
            setFormState({ open: false, editing: null })
            invalidate()
          }}
        />
      )}
    </>
  )
}

function ContractModal({
  editing,
  suppliers,
  onClose,
  onSuccess,
}: {
  editing: PriceContract | null
  suppliers: { id: number; code: string; name: string }[]
  onClose: () => void
  onSuccess: () => void
}) {
  const [form] = Form.useForm<{
    supplierId: number
    materialId: number
    contractPrice: number
    effectiveFrom: Dayjs
    effectiveTo: Dayjs
    status: boolean
    remark?: string
  }>()
  const materials = useEnabledMaterials()
  const saveMut = useMutation({
    mutationFn: async (v: {
      supplierId: number
      materialId: number
      contractPrice: number
      effectiveFrom: Dayjs
      effectiveTo: Dayjs
      status: boolean
      remark?: string
    }) => {
      const payload = {
        supplierId: v.supplierId,
        materialId: v.materialId,
        contractPrice: v.contractPrice,
        effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
        effectiveTo: v.effectiveTo.format('YYYY-MM-DD'),
        status: v.status ? 1 : 0,
        remark: v.remark,
      }
      if (editing) await updatePriceContract(editing.id, payload)
      else await createPriceContract(payload)
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功')
      onSuccess()
    },
  })
  return (
    <Modal
      title={editing ? `编辑合同价 #${editing.id}` : '新增合同价'}
      open
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={saveMut.isPending}
      destroyOnClose
      afterOpenChange={(visible) => {
        if (!visible) return
        if (editing) {
          form.setFieldsValue({
            supplierId: editing.supplierId,
            materialId: editing.materialId,
            contractPrice: editing.contractPrice,
            effectiveFrom: dayjs(editing.effectiveFrom),
            effectiveTo: dayjs(editing.effectiveTo),
            status: editing.status === 1,
            remark: editing.remark ?? undefined,
          })
        } else {
          form.resetFields()
          form.setFieldsValue({ status: true, effectiveFrom: dayjs(), effectiveTo: dayjs().add(1, 'year') })
        }
      }}
    >
      <Form form={form} layout="vertical" onFinish={(v) => saveMut.mutate(v)}>
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item name="supplierId" label="供应商" rules={[{ required: true, message: '请选择供应商' }]}>
              <Select showSearch optionFilterProp="label" options={suppliers.map((s) => ({ value: s.id, label: `${s.code} ${s.name}` }))} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="materialId" label="物料" rules={[{ required: true, message: '请选择物料' }]}>
              <Select showSearch optionFilterProp="label" options={materials.map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="contractPrice" label="合同单价（不含税）" rules={[{ required: true, message: '请输入合同价' }]}>
              <InputNumber min={0.0001} precision={4} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item name="effectiveFrom" label="生效起" rules={[{ required: true, message: '请选择' }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item name="effectiveTo" label="生效止" rules={[{ required: true, message: '请选择' }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="status" label="状态" valuePropName="checked">
              <Select
                options={[
                  { value: true, label: '启用' },
                  { value: false, label: '停用' },
                ]}
              />
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item name="remark" label="备注（合同号等）">
              <Input maxLength={200} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  )
}

/* ===================== Tab3：等级价 ===================== */

function LevelTab() {
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [formState, setFormState] = useState<{ open: boolean; editing: PriceLevel | null }>({ open: false, editing: null })

  const params = { pageNum, pageSize }
  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['price', 'levels', params],
    queryFn: () => getPriceLevels(params),
  })
  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['price', 'levels'] })
  const deleteMut = useMutation({
    mutationFn: (id: number) => deletePriceLevel(id),
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
  })

  const columns: ColumnsType<PriceLevel> = [
    {
      title: '物料',
      dataIndex: 'materialCode',
      width: 240,
      ellipsis: true,
      render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}`,
    },
    {
      title: '客户等级',
      dataIndex: 'level',
      width: 100,
      render: (v: string) => <Tag color={v === 'A' ? 'gold' : v === 'C' ? 'default' : 'blue'}>{v}</Tag>,
    },
    { title: '单价(不含税)', dataIndex: 'price', width: 120, align: 'right', render: (v: number) => <Text strong>{fmtPrice(v)}</Text> },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number) => (v === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size={0}>
          <HasPerm code="basedata:price:edit">
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => setFormState({ open: true, editing: record })}>
              编辑
            </Button>
          </HasPerm>
          <HasPerm code="basedata:price:delete">
            <Popconfirm title="确认删除该等级价？" onConfirm={() => deleteMut.mutate(record.id)}>
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </HasPerm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <HasPerm code="basedata:price:add">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setFormState({ open: true, editing: null })}>
            新增等级价
          </Button>
        </HasPerm>
        <Text type="secondary" style={{ fontSize: 12 }}>
          定价引擎优先级：特价审批 &gt; 等级价（物料×客户等级，唯一） &gt; 批量价 &gt; 参考成本×加成系数
        </Text>
      </Space>
      <Table<PriceLevel>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading || isFetching}
        pagination={{
          total: data?.total ?? 0,
          current: data?.pageNum ?? 1,
          pageSize: data?.pageSize ?? 10,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPageNum(p)
            setPageSize(s)
          },
        }}
      />
      {formState.open && (
        <LevelModal
          key={formState.editing?.id ?? 'new'}
          editing={formState.editing}
          onClose={() => setFormState({ open: false, editing: null })}
          onSuccess={() => {
            setFormState({ open: false, editing: null })
            invalidate()
          }}
        />
      )}
    </>
  )
}

function LevelModal({ editing, onClose, onSuccess }: { editing: PriceLevel | null; onClose: () => void; onSuccess: () => void }) {
  const [form] = Form.useForm<{ materialId: number; level: string; price: number; status: boolean; remark?: string }>()
  const materials = useEnabledMaterials()
  const saveMut = useMutation({
    mutationFn: async (v: { materialId: number; level: string; price: number; status: boolean; remark?: string }) => {
      const payload = {
        materialId: v.materialId,
        level: v.level,
        price: v.price,
        status: v.status ? 1 : 0,
        remark: v.remark,
      }
      if (editing) await updatePriceLevel(editing.id, payload)
      else await createPriceLevel(payload)
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功')
      onSuccess()
    },
  })
  return (
    <Modal
      title={editing ? `编辑等级价 #${editing.id}` : '新增等级价（物料×等级 唯一）'}
      open
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={saveMut.isPending}
      destroyOnClose
      afterOpenChange={(visible) => {
        if (!visible) return
        if (editing) {
          form.setFieldsValue({
            materialId: editing.materialId,
            level: editing.level,
            price: editing.price,
            status: editing.status === 1,
            remark: editing.remark ?? undefined,
          })
        } else {
          form.resetFields()
          form.setFieldsValue({ status: true, level: 'A' })
        }
      }}
    >
      <Form form={form} layout="vertical" onFinish={(v) => saveMut.mutate(v)}>
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item name="materialId" label="物料" rules={[{ required: true, message: '请选择物料' }]}>
              <Select showSearch optionFilterProp="label" options={materials.map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="level" label="客户等级" rules={[{ required: true, message: '请选择等级' }]}>
              <Select options={LEVEL_OPTIONS} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="price" label="单价（不含税）" rules={[{ required: true, message: '请输入单价' }]}>
              <InputNumber min={0.0001} precision={4} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="status" label="状态">
              <Select
                options={[
                  { value: true, label: '启用' },
                  { value: false, label: '停用' },
                ]}
              />
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item name="remark" label="备注">
              <Input maxLength={200} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  )
}

/* ===================== Tab4：批量价 ===================== */

function QtyTab() {
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [formState, setFormState] = useState<{ open: boolean; editing: PriceQty | null }>({ open: false, editing: null })

  const params = { pageNum, pageSize }
  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['price', 'qtys', params],
    queryFn: () => getPriceQtys(params),
  })
  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['price', 'qtys'] })
  const deleteMut = useMutation({
    mutationFn: (id: number) => deletePriceQty(id),
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
  })

  const columns: ColumnsType<PriceQty> = [
    {
      title: '物料',
      dataIndex: 'materialCode',
      width: 240,
      ellipsis: true,
      render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}`,
    },
    { title: '起订量(≥)', dataIndex: 'minQty', width: 110, align: 'right' },
    { title: '单价(不含税)', dataIndex: 'price', width: 120, align: 'right', render: (v: number) => <Text strong>{fmtPrice(v)}</Text> },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number) => (v === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size={0}>
          <HasPerm code="basedata:price:edit">
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => setFormState({ open: true, editing: record })}>
              编辑
            </Button>
          </HasPerm>
          <HasPerm code="basedata:price:delete">
            <Popconfirm title="确认删除该批量价？" onConfirm={() => deleteMut.mutate(record.id)}>
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </HasPerm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <HasPerm code="basedata:price:add">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setFormState({ open: true, editing: null })}>
            新增批量价
          </Button>
        </HasPerm>
        <Text type="secondary" style={{ fontSize: 12 }}>
          阶梯定价：命中「满足数量的最高起订量档」（物料+起订量唯一，起订量越大价应越低）
        </Text>
      </Space>
      <Table<PriceQty>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading || isFetching}
        pagination={{
          total: data?.total ?? 0,
          current: data?.pageNum ?? 1,
          pageSize: data?.pageSize ?? 10,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPageNum(p)
            setPageSize(s)
          },
        }}
      />
      {formState.open && (
        <QtyModal
          key={formState.editing?.id ?? 'new'}
          editing={formState.editing}
          onClose={() => setFormState({ open: false, editing: null })}
          onSuccess={() => {
            setFormState({ open: false, editing: null })
            invalidate()
          }}
        />
      )}
    </>
  )
}

function QtyModal({ editing, onClose, onSuccess }: { editing: PriceQty | null; onClose: () => void; onSuccess: () => void }) {
  const [form] = Form.useForm<{ materialId: number; minQty: number; price: number; status: boolean; remark?: string }>()
  const materials = useEnabledMaterials()
  const saveMut = useMutation({
    mutationFn: async (v: { materialId: number; minQty: number; price: number; status: boolean; remark?: string }) => {
      const payload = {
        materialId: v.materialId,
        minQty: v.minQty,
        price: v.price,
        status: v.status ? 1 : 0,
        remark: v.remark,
      }
      if (editing) await updatePriceQty(editing.id, payload)
      else await createPriceQty(payload)
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功')
      onSuccess()
    },
  })
  return (
    <Modal
      title={editing ? `编辑批量价 #${editing.id}` : '新增批量价（物料×起订量 唯一）'}
      open
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={saveMut.isPending}
      destroyOnClose
      afterOpenChange={(visible) => {
        if (!visible) return
        if (editing) {
          form.setFieldsValue({
            materialId: editing.materialId,
            minQty: editing.minQty,
            price: editing.price,
            status: editing.status === 1,
            remark: editing.remark ?? undefined,
          })
        } else {
          form.resetFields()
          form.setFieldsValue({ status: true, minQty: 1 })
        }
      }}
    >
      <Form form={form} layout="vertical" onFinish={(v) => saveMut.mutate(v)}>
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item name="materialId" label="物料" rules={[{ required: true, message: '请选择物料' }]}>
              <Select showSearch optionFilterProp="label" options={materials.map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="minQty" label="起订量（≥ 该数量命中本档）" rules={[{ required: true, message: '请输入起订量' }]}>
              <InputNumber min={0.0001} precision={4} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="price" label="单价（不含税）" rules={[{ required: true, message: '请输入单价' }]}>
              <InputNumber min={0.0001} precision={4} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="status" label="状态">
              <Select
                options={[
                  { value: true, label: '启用' },
                  { value: false, label: '停用' },
                ]}
              />
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item name="remark" label="备注">
              <Input maxLength={200} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  )
}

/* ===================== Tab5：特价审批 ===================== */

/** 特价申请状态标签 */
function SpecialStatusTag({ status, wfStarted }: { status: number; wfStarted: number }) {
  if (status === 10) return <Tag color="success">已通过</Tag>
  if (status === 20) return <Tag color="error">已驳回</Tag>
  return wfStarted === 1 ? <Tag color="processing">审批流中</Tag> : <Tag color="warning">待审</Tag>
}

function SpecialTab() {
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [status, setStatus] = useState<number | undefined>()
  const [applyOpen, setApplyOpen] = useState(false)
  const [auditState, setAuditState] = useState<{ open: boolean; id: number | null; approve: boolean }>({
    open: false,
    id: null,
    approve: true,
  })

  const params = { pageNum, pageSize, status }
  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['price', 'specials', params],
    queryFn: () => getPriceSpecials(params),
  })
  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['price', 'specials'] })

  const deleteMut = useMutation({
    mutationFn: (id: number) => deletePriceSpecial(id),
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
  })

  const columns: ColumnsType<PriceSpecial> = [
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 180,
      ellipsis: true,
      render: (_, r) => (
        <Space size={4}>
          <span>{r.customerName ?? '-'}</span>
          {r.customerLevel ? <Tag color={r.customerLevel === 'A' ? 'gold' : r.customerLevel === 'C' ? 'default' : 'blue'}>{r.customerLevel}</Tag> : null}
        </Space>
      ),
    },
    {
      title: '物料',
      dataIndex: 'materialCode',
      width: 220,
      ellipsis: true,
      render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}`,
    },
    { title: '特价(不含税)', dataIndex: 'specialPrice', width: 110, align: 'right', render: (v: number) => <Text strong style={{ color: '#c53030' }}>{fmtPrice(v)}</Text> },
    { title: '申请理由', dataIndex: 'applyReason', width: 180, ellipsis: true },
    {
      title: '生效期',
      dataIndex: 'validFrom',
      width: 180,
      render: (_, r) => (r.validFrom || r.validTo ? `${r.validFrom ?? '…'} ~ ${r.validTo ?? '长期'}` : '长期'),
    },
    { title: '状态', dataIndex: 'status', width: 100, render: (v: number, r) => <SpecialStatusTag status={v} wfStarted={r.wfStarted} /> },
    { title: '审批意见', dataIndex: 'auditRemark', width: 150, ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 210,
      fixed: 'right',
      render: (_, record) => (
        <Space size={0}>
          {record.status === 0 && (
            <HasPerm code="sales:priceSpecial:audit">
              <Button
                type="link"
                size="small"
                onClick={() => setAuditState({ open: true, id: record.id, approve: true })}
              >
                通过
              </Button>
              <Button
                type="link"
                size="small"
                danger
                onClick={() => setAuditState({ open: true, id: record.id, approve: false })}
              >
                驳回
              </Button>
            </HasPerm>
          )}
          {(record.status === 0 || record.status === 20) && (
            <HasPerm code="sales:priceSpecial:delete">
              <Popconfirm title="确认删除该特价申请？" onConfirm={() => deleteMut.mutate(record.id)}>
                <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                  删除
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          placeholder="状态"
          style={{ width: 130 }}
          allowClear
          options={[
            { value: 0, label: '待审' },
            { value: 10, label: '已通过' },
            { value: 20, label: '已驳回' },
          ]}
          onChange={(v) => {
            setPageNum(1)
            setStatus(v)
          }}
        />
        <HasPerm code="sales:priceSpecial:add">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setApplyOpen(true)}>
            特价申请
          </Button>
        </HasPerm>
        <Text type="secondary" style={{ fontSize: 12 }}>
          申请自动尝试挂审批流（无启用流程时由管理员在此审批）；通过后为定价引擎最高优先级
        </Text>
      </Space>
      <Table<PriceSpecial>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading || isFetching}
        scroll={{ x: 1300 }}
        pagination={{
          total: data?.total ?? 0,
          current: data?.pageNum ?? 1,
          pageSize: data?.pageSize ?? 10,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPageNum(p)
            setPageSize(s)
          },
        }}
      />
      {applyOpen && (
        <SpecialApplyModal
          onClose={() => setApplyOpen(false)}
          onSuccess={() => {
            setApplyOpen(false)
            invalidate()
          }}
        />
      )}
      {auditState.open && auditState.id != null && (
        <SpecialAuditModal
          id={auditState.id}
          approve={auditState.approve}
          onClose={() => setAuditState({ open: false, id: null, approve: true })}
          onSuccess={() => {
            setAuditState({ open: false, id: null, approve: true })
            invalidate()
          }}
        />
      )}
    </>
  )
}

function SpecialApplyModal({ onClose, onSuccess }: { onClose: () => void; onSuccess: () => void }) {
  const [form] = Form.useForm<{
    customerId: number
    materialId: number
    specialPrice: number
    applyReason?: string
    validFrom?: Dayjs | null
    validTo?: Dayjs | null
    remark?: string
  }>()
  const customers = useQuery({
    queryKey: ['price', 'options', 'customers'],
    queryFn: () => getEnabledCustomers(),
    staleTime: 5 * 60_000,
  }).data
  const materials = useEnabledMaterials()
  const saveMut = useMutation({
    mutationFn: (v: {
      customerId: number
      materialId: number
      specialPrice: number
      applyReason?: string
      validFrom?: Dayjs | null
      validTo?: Dayjs | null
      remark?: string
    }) =>
      applyPriceSpecial({
        customerId: v.customerId,
        materialId: v.materialId,
        specialPrice: v.specialPrice,
        applyReason: v.applyReason,
        validFrom: v.validFrom ? v.validFrom.format('YYYY-MM-DD') : null,
        validTo: v.validTo ? v.validTo.format('YYYY-MM-DD') : null,
        remark: v.remark,
      }),
    onSuccess: () => {
      message.success('特价申请已提交（存在审批流则生成待办，否则等待管理员审批）')
      onSuccess()
    },
  })
  return (
    <Modal
      title="特价申请"
      open
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={saveMut.isPending}
      destroyOnClose
    >
      <Form form={form} layout="vertical" onFinish={(v) => saveMut.mutate(v)}>
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item name="customerId" label="客户" rules={[{ required: true, message: '请选择客户' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                options={(customers ?? []).map((c) => ({ value: c.id, label: `${c.code} ${c.name}` }))}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="materialId" label="物料" rules={[{ required: true, message: '请选择物料' }]}>
              <Select showSearch optionFilterProp="label" options={materials.map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="specialPrice" label="特价（不含税）" rules={[{ required: true, message: '请输入特价' }]}>
              <InputNumber min={0.0001} precision={4} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item name="validFrom" label="生效起">
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item name="validTo" label="生效止">
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item name="applyReason" label="申请理由">
              <Input.TextArea rows={2} maxLength={200} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  )
}

function SpecialAuditModal({
  id,
  approve,
  onClose,
  onSuccess,
}: {
  id: number
  approve: boolean
  onClose: () => void
  onSuccess: () => void
}) {
  const [form] = Form.useForm<{ remark?: string }>()
  const saveMut = useMutation({
    mutationFn: (v: { remark?: string }) =>
      approve ? approvePriceSpecial(id, v.remark) : rejectPriceSpecial(id, v.remark),
    onSuccess: () => {
      message.success(approve ? '已通过' : '已驳回')
      onSuccess()
    },
  })
  return (
    <Modal
      title={approve ? `通过特价申请 #${id}` : `驳回特价申请 #${id}`}
      open
      onCancel={onClose}
      onOk={() => form.submit()}
      okText={approve ? '通过' : '驳回'}
      okButtonProps={{ danger: !approve, loading: saveMut.isPending }}
      destroyOnClose
    >
      <Form form={form} layout="vertical" onFinish={(v) => saveMut.mutate(v)}>
        <Form.Item name="remark" label="审批意见（可选）">
          <Input.TextArea rows={2} maxLength={200} />
        </Form.Item>
      </Form>
    </Modal>
  )
}

/* ===================== 共享下拉 ===================== */

/** 启用供应商下拉数据（价格中心各 Tab 复用） */
function useEnabledSuppliers(): { id: number; code: string; name: string }[] {
  return (
    useQuery({
      queryKey: ['price', 'options', 'suppliers'],
      queryFn: () => getEnabledSuppliers(),
      staleTime: 5 * 60_000,
    }).data ?? []
  )
}

/** 启用物料下拉数据 */
function useEnabledMaterials(): { id: number; code: string; name: string }[] {
  return (
    useQuery({
      queryKey: ['price', 'options', 'materials'],
      queryFn: () => getEnabledMaterials(),
      staleTime: 5 * 60_000,
    }).data ?? []
  )
}
