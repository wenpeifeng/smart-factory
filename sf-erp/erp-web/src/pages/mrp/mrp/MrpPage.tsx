import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Col,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlayCircleOutlined, PlusOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  calcApi,
  mpsApi,
  suggestionApi,
  type CalcBatchRow,
  type CalcDetailTreeNode,
  type MpsRow,
  type SuggestionRow,
} from '@/api/mrp'
import MaterialSelect from '../components/MaterialSelect'

const { Title, Text } = Typography

const MPS_STATUS: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待运算', color: 'processing' },
  CALCULATED: { label: '已运算', color: 'default' },
}

const PLAN_TYPE: Record<string, { label: string; color: string }> = {
  PURCHASE: { label: '采购建议', color: 'orange' },
  PRODUCE: { label: '生产建议', color: 'geekblue' },
}

/** 展开明细树 → AntD Table 树形行 */
interface DetailRow {
  key: string
  level: number
  code: string | null
  name: string
  unit: string | null
  grossQty: number
  stockQty: number
  onwayQty: number
  netQty: number
  suggestQty: number | null
  suggestDate: string | null
  planType: string | null
  children?: DetailRow[]
}

function toDetailRows(node: CalcDetailTreeNode): DetailRow {
  return {
    key: node.nodePath,
    level: node.level,
    code: node.code,
    name: node.name,
    unit: node.unit,
    grossQty: node.grossQty,
    stockQty: node.stockQty,
    onwayQty: node.onwayQty,
    netQty: node.netQty,
    suggestQty: node.suggestQty,
    suggestDate: node.suggestDate,
    planType: node.planType,
    children: node.children.length ? node.children.map(toDetailRows) : undefined,
  }
}

const DETAIL_COLUMNS: ColumnsType<DetailRow> = [
  { title: '层级', dataIndex: 'level', width: 64 },
  { title: '物料编码', dataIndex: 'code', width: 120 },
  { title: '物料名称', dataIndex: 'name', ellipsis: true },
  { title: '毛需求', dataIndex: 'grossQty', width: 100, align: 'right' },
  { title: '现有库存', dataIndex: 'stockQty', width: 100, align: 'right' },
  { title: '在途', dataIndex: 'onwayQty', width: 90, align: 'right' },
  {
    title: '净需求',
    dataIndex: 'netQty',
    width: 100,
    align: 'right',
    render: (v: number) => (v > 0 ? <Text type="danger">{v}</Text> : v),
  },
  { title: '建议数量', dataIndex: 'suggestQty', width: 100, align: 'right' },
  { title: '建议日期', dataIndex: 'suggestDate', width: 110 },
  {
    title: '建议类型',
    dataIndex: 'planType',
    width: 100,
    render: (v: string | null) => {
      const t = v ? PLAN_TYPE[v] : undefined
      return t ? <Tag color={t.color}>{t.label}</Tag> : <Tag>无建议</Tag>
    },
  },
]

/**
 * MRP 运算页：MPS 录入 → 执行运算 → 批次/建议结果（类型 Tab）+ 可折叠的全树展开明细。
 */
export default function MrpPage() {
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState('mps')

  // MPS 列表状态
  const [mpsKeyword, setMpsKeyword] = useState('')
  const [mpsStatus, setMpsStatus] = useState<string | undefined>()
  const [mpsPage, setMpsPage] = useState(1)

  // MPS 编辑弹窗
  const [mpsEditorOpen, setMpsEditorOpen] = useState(false)
  const [editingMpsId, setEditingMpsId] = useState<number | null>(null)
  const [mpsForm, setMpsForm] = useState<{
    materialId?: number
    qty: number
    startDate?: string
    remark: string
  }>({ qty: 10, startDate: dayjs().add(7, 'day').format('YYYY-MM-DD'), remark: '' })

  // 运算弹窗
  const [runOpen, setRunOpen] = useState(false)
  const [selectedMpsIds, setSelectedMpsIds] = useState<number[]>([])
  const [runRemark, setRunRemark] = useState('')

  // 结果区状态
  const [batchPage, setBatchPage] = useState(1)
  const [activeBatch, setActiveBatch] = useState<string | null>(null)
  const [suggestionTab, setSuggestionTab] = useState('ALL')
  const [resultView, setResultView] = useState<'suggestions' | 'details'>('suggestions')

  // ==================== 查询 ====================
  const { data: mpsData, isLoading: mpsLoading, isFetching: mpsFetching } = useQuery({
    queryKey: ['mrp', 'mps', mpsKeyword, mpsStatus, mpsPage],
    queryFn: () =>
      mpsApi.page({ pageNum: mpsPage, pageSize: 10, keyword: mpsKeyword || undefined, status: mpsStatus }),
  })

  const { data: pendingMps } = useQuery({
    queryKey: ['mrp', 'mps', 'pending-for-run'],
    queryFn: () => mpsApi.page({ pageNum: 1, pageSize: 100, status: 'PENDING' }),
    enabled: runOpen,
  })

  const { data: batchData, isLoading: batchLoading } = useQuery({
    queryKey: ['mrp', 'calc-batches', batchPage],
    queryFn: () => calcApi.batches({ pageNum: batchPage, pageSize: 10 }),
  })

  const { data: suggestionData, isLoading: suggestionLoading } = useQuery({
    queryKey: ['mrp', 'suggestions', activeBatch, suggestionTab],
    queryFn: () =>
      suggestionApi.page({
        pageNum: 1,
        pageSize: 100,
        batchNo: activeBatch ?? undefined,
        planType: suggestionTab === 'ALL' ? undefined : suggestionTab,
      }),
    enabled: activeBatch != null,
  })

  const { data: detailData, isLoading: detailLoading } = useQuery({
    queryKey: ['mrp', 'batch-details', activeBatch],
    queryFn: () => calcApi.batchDetails(activeBatch as string),
    enabled: activeBatch != null && resultView === 'details',
  })

  // ==================== 变更 ====================
  const mpsSaveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        materialId: mpsForm.materialId as number,
        qty: mpsForm.qty,
        startDate: mpsForm.startDate as string,
        remark: mpsForm.remark || undefined,
      }
      if (editingMpsId) await mpsApi.update(editingMpsId, payload)
      else await mpsApi.create(payload)
    },
    onSuccess: () => {
      message.success('主产计划已保存')
      setMpsEditorOpen(false)
      queryClient.invalidateQueries({ queryKey: ['mrp', 'mps'] })
    },
  })

  const mpsDeleteMutation = useMutation({
    mutationFn: (id: number) => mpsApi.remove(id),
    onSuccess: () => {
      message.success('计划行已删除')
      queryClient.invalidateQueries({ queryKey: ['mrp', 'mps'] })
    },
  })

  const runMutation = useMutation({
    mutationFn: () =>
      calcApi.run({
        mpsIds: selectedMpsIds.length ? selectedMpsIds : undefined,
        remark: runRemark || undefined,
      }),
    onSuccess: (result) => {
      message.success(`MRP 运算完成：批次 ${result.batchNo}，产出建议 ${result.suggestionCount} 条`)
      setRunOpen(false)
      setActiveBatch(result.batchNo)
      setResultView('suggestions')
      setActiveTab('result')
      queryClient.invalidateQueries({ queryKey: ['mrp', 'calc-batches'] })
      queryClient.invalidateQueries({ queryKey: ['mrp', 'mps'] })
    },
  })

  const toWorkOrderMutation = useMutation({
    mutationFn: (id: number) => suggestionApi.toWorkOrder(id),
    onSuccess: (woId) => {
      message.success(`已生成生产订单草稿（ID: ${woId}），请到“生产订单”页下达`)
      queryClient.invalidateQueries({ queryKey: ['mrp', 'suggestions'] })
    },
  })

  const toPurchaseMutation = useMutation({
    mutationFn: (id: number) => suggestionApi.toPurchase(id),
    onSuccess: (poId) => {
      message.success(`已生成采购订单草稿（ID: ${poId}），请到“采购订单”页补填供应商/单价并审核`)
      queryClient.invalidateQueries({ queryKey: ['mrp', 'suggestions'] })
    },
  })

  // ==================== MPS 表格 ====================
  const mpsColumns: ColumnsType<MpsRow> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '成品编码', dataIndex: 'materialCode', width: 120 },
    { title: '成品名称', dataIndex: 'materialName', ellipsis: true },
    { title: '计划数量', dataIndex: 'qty', width: 100, align: 'right' },
    { title: '计划开工日期', dataIndex: 'startDate', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const s = MPS_STATUS[v]
        return s ? <Tag color={s.color}>{s.label}</Tag> : v
      },
    },
    { title: '备注', dataIndex: 'remark', width: 140, ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'op',
      width: 130,
      render: (_, row) => (
        <Space size={4}>
          {row.status === 'PENDING' && (
            <>
              <Button
                size="small"
                onClick={() => {
                  setEditingMpsId(row.id)
                  setMpsForm({
                    materialId: row.materialId,
                    qty: row.qty,
                    startDate: row.startDate,
                    remark: row.remark ?? '',
                  })
                  setMpsEditorOpen(true)
                }}
              >
                编辑
              </Button>
              <Popconfirm title="删除该计划行？" onConfirm={() => mpsDeleteMutation.mutate(row.id)}>
                <Button size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ]

  // ==================== 建议表格 ====================
  const suggestionColumns: ColumnsType<SuggestionRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 120 },
    { title: '物料名称', dataIndex: 'materialName', ellipsis: true },
    {
      title: '建议类型',
      dataIndex: 'planType',
      width: 100,
      render: (v: string) => {
        const t = PLAN_TYPE[v]
        return t ? <Tag color={t.color}>{t.label}</Tag> : v
      },
    },
    { title: '建议数量', dataIndex: 'qty', width: 100, align: 'right' },
    { title: '建议日期', dataIndex: 'suggestDate', width: 110 },
    { title: '层级', dataIndex: 'level', width: 70 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) =>
        v === 'PENDING' ? <Tag color="processing">待处理</Tag> : <Tag color="success">已转单</Tag>,
    },
    {
      title: '操作',
      key: 'op',
      width: 170,
      render: (_, row) => (
        <Space size={4}>
          {row.planType === 'PRODUCE' && row.status === 'PENDING' && (
            <Popconfirm
              title="按建议生成生产订单草稿？"
              onConfirm={() => toWorkOrderMutation.mutate(row.id)}
            >
              <Button size="small" type="primary" loading={toWorkOrderMutation.isPending}>
                转生产
              </Button>
            </Popconfirm>
          )}
          {row.planType === 'PURCHASE' && row.status === 'PENDING' && (
            <Popconfirm
              title="按建议生成采购订单草稿？（供应商/单价稍后在采购订单页补填）"
              onConfirm={() => toPurchaseMutation.mutate(row.id)}
            >
              <Button size="small" type="primary" ghost loading={toPurchaseMutation.isPending}>
                转采购
              </Button>
            </Popconfirm>
          )}
          {row.status === 'CONVERTED' && <Text type="secondary">订单 #{row.refId}</Text>}
        </Space>
      ),
    },
  ]

  const batchColumns: ColumnsType<CalcBatchRow> = [
    { title: '运算批次号', dataIndex: 'batchNo', width: 150 },
    { title: 'MPS 行数', dataIndex: 'mpsCount', width: 90, align: 'right' },
    { title: '建议条数', dataIndex: 'suggestionCount', width: 90, align: 'right' },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    { title: '运算时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      key: 'op',
      width: 100,
      render: (_, row) => (
        <Button
          size="small"
          type={activeBatch === row.batchNo ? 'primary' : 'default'}
          ghost={activeBatch === row.batchNo}
          onClick={() => {
            setActiveBatch(row.batchNo)
            setResultView('suggestions')
          }}
        >
          查看结果
        </Button>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        MRP 运算
      </Title>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'mps',
            label: '主产计划 (MPS)',
            children: (
              <Card>
                <Space style={{ marginBottom: 16 }} wrap>
                  <Input.Search
                    placeholder="成品物料编码 / 名称搜索"
                    style={{ width: 240 }}
                    allowClear
                    onSearch={(v) => {
                      setMpsPage(1)
                      setMpsKeyword(v)
                    }}
                  />
                  <Select
                    placeholder="状态筛选"
                    allowClear
                    style={{ width: 130 }}
                    value={mpsStatus}
                    onChange={(v) => {
                      setMpsPage(1)
                      setMpsStatus(v)
                    }}
                    options={[
                      { value: 'PENDING', label: '待运算' },
                      { value: 'CALCULATED', label: '已运算' },
                    ]}
                  />
                  <Button
                    type="primary"
                    icon={<PlayCircleOutlined />}
                    onClick={() => {
                      setSelectedMpsIds([])
                      setRunRemark('')
                      setRunOpen(true)
                    }}
                  >
                    执行 MRP 运算
                  </Button>
                  <Button
                    icon={<PlusOutlined />}
                    onClick={() => {
                      setEditingMpsId(null)
                      setMpsForm({
                        qty: 10,
                        startDate: dayjs().add(7, 'day').format('YYYY-MM-DD'),
                        remark: '',
                      })
                      setMpsEditorOpen(true)
                    }}
                  >
                    录入计划
                  </Button>
                </Space>
                <Table<MpsRow>
                  rowKey="id"
                  columns={mpsColumns}
                  dataSource={mpsData?.records ?? []}
                  loading={mpsLoading || mpsFetching}
                  pagination={{
                    total: mpsData?.total ?? 0,
                    current: mpsData?.pageNum ?? 1,
                    pageSize: mpsData?.pageSize ?? 10,
                    showTotal: (t) => `共 ${t} 条`,
                    onChange: (p) => setMpsPage(p),
                  }}
                />
              </Card>
            ),
          },
          {
            key: 'result',
            label: '运算结果',
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size={16}>
                <Card title="运算批次" size="small">
                  <Table<CalcBatchRow>
                    rowKey="id"
                    size="small"
                    columns={batchColumns}
                    dataSource={batchData?.records ?? []}
                    loading={batchLoading}
                    pagination={{
                      total: batchData?.total ?? 0,
                      current: batchData?.pageNum ?? 1,
                      pageSize: batchData?.pageSize ?? 10,
                      onChange: (p) => setBatchPage(p),
                    }}
                  />
                </Card>

                {activeBatch ? (
                  <Card
                    title={`批次 ${activeBatch} 结果`}
                    size="small"
                    extra={
                      <RadioGroupish
                        value={resultView}
                        onChange={setResultView}
                      />
                    }
                  >
                    {resultView === 'suggestions' ? (
                      <Tabs
                        activeKey={suggestionTab}
                        onChange={setSuggestionTab}
                        items={[
                          { key: 'ALL', label: '全部建议' },
                          { key: 'PRODUCE', label: '生产建议' },
                          { key: 'PURCHASE', label: '采购建议' },
                        ]}
                      />
                    ) : null}
                    {resultView === 'suggestions' ? (
                      suggestionLoading ? (
                        <Spin />
                      ) : (
                        <Table<SuggestionRow>
                          rowKey="id"
                          size="small"
                          columns={suggestionColumns}
                          dataSource={suggestionData?.records ?? []}
                          pagination={false}
                        />
                      )
                    ) : detailLoading ? (
                      <Spin />
                    ) : (
                      <Table<DetailRow>
                        rowKey="key"
                        size="small"
                        columns={DETAIL_COLUMNS}
                        dataSource={(detailData?.trees ?? []).map(toDetailRows)}
                        pagination={false}
                        defaultExpandAllRows
                        indentSize={20}
                      />
                    )}
                  </Card>
                ) : (
                  <Card>
                    <Text type="secondary">选择上方批次查看建议与全树展开明细</Text>
                  </Card>
                )}
              </Space>
            ),
          },
        ]}
      />

      {/* MPS 编辑弹窗 */}
      <Modal
        title={editingMpsId ? `编辑计划 #${editingMpsId}` : '录入主产计划'}
        open={mpsEditorOpen}
        onCancel={() => setMpsEditorOpen(false)}
        onOk={() => {
          if (!mpsForm.materialId) return message.warning('请选择成品物料')
          if (!mpsForm.startDate) return message.warning('请选择开工日期')
          mpsSaveMutation.mutate()
        }}
        confirmLoading={mpsSaveMutation.isPending}
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div>
            <div style={{ marginBottom: 4 }}>成品物料</div>
            <MaterialSelect
              value={mpsForm.materialId}
              onChange={(v) => setMpsForm((f) => ({ ...f, materialId: v }))}
            />
          </div>
          <Row gutter={12}>
            <Col span={12}>
              <div style={{ marginBottom: 4 }}>计划数量</div>
              <InputNumber
                style={{ width: '100%' }}
                min={0.0001}
                value={mpsForm.qty}
                onChange={(v) => setMpsForm((f) => ({ ...f, qty: v ?? 1 }))}
              />
            </Col>
            <Col span={12}>
              <div style={{ marginBottom: 4 }}>计划开工日期</div>
              <DatePicker
                style={{ width: '100%' }}
                value={mpsForm.startDate ? dayjs(mpsForm.startDate) : undefined}
                onChange={(d) =>
                  setMpsForm((f) => ({ ...f, startDate: d ? d.format('YYYY-MM-DD') : undefined }))
                }
              />
            </Col>
          </Row>
          <div>
            <div style={{ marginBottom: 4 }}>备注</div>
            <Input
              value={mpsForm.remark}
              onChange={(e) => setMpsForm((f) => ({ ...f, remark: e.target.value }))}
            />
          </div>
        </Space>
      </Modal>

      {/* MRP 运算弹窗 */}
      <Modal
        title="执行 MRP 运算"
        open={runOpen}
        onCancel={() => setRunOpen(false)}
        onOk={() => runMutation.mutate()}
        confirmLoading={runMutation.isPending}
        width={720}
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Text type="secondary">
            净需求 = 毛需求 − 现有库存 − 在途；子件毛需求 = 上层净需求 × BOM用量 × (1+损耗率)。
            不勾选任何行 = 运算全部待运算计划。
          </Text>
          <Table<MpsRow>
            rowKey="id"
            size="small"
            dataSource={pendingMps?.records ?? []}
            loading={!runOpen || pendingMps === undefined}
            pagination={false}
            rowSelection={{
              selectedRowKeys: selectedMpsIds,
              onChange: (keys) => setSelectedMpsIds(keys as number[]),
            }}
            columns={[
              { title: '成品编码', dataIndex: 'materialCode', width: 120 },
              { title: '成品名称', dataIndex: 'materialName', ellipsis: true },
              { title: '数量', dataIndex: 'qty', width: 90, align: 'right' },
              { title: '开工日期', dataIndex: 'startDate', width: 110 },
            ]}
            locale={{ emptyText: '暂无待运算的计划行，请先录入主产计划' }}
          />
          <Input
            placeholder="运算备注（可选）"
            value={runRemark}
            onChange={(e) => setRunRemark(e.target.value)}
          />
        </Space>
      </Modal>
    </div>
  )
}

/** 结果视图切换（建议 / 展开明细） */
function RadioGroupish({
  value,
  onChange,
}: {
  value: 'suggestions' | 'details'
  onChange: (v: 'suggestions' | 'details') => void
}) {
  return (
    <Select
      value={value}
      onChange={onChange}
      style={{ width: 150 }}
      options={[
        { value: 'suggestions', label: '发补建议' },
        { value: 'details', label: '全树展开明细' },
      ]}
    />
  )
}
