import HasPerm from '@/components/HasPerm'
import StatusTag from '@/components/StatusTag'
import { useMemo, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Col,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  ReloadOutlined,
  SendOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  TASK_STATUS_MAP,
  TASK_TYPE_MAP,
  claimWmsTask,
  completeWmsTask,
  createWmsManualTask,
  fetchAbcCounts,
  fetchWmsKpi,
  fetchWmsTaskBoard,
  fetchWmsTaskPage,
  fetchWarehouseOptions,
  fmtQty,
  recalcAbc,
  startWmsTask,
  triggerWmsScan,
  cancelWmsTask,
  type WmsTaskRow,
} from '@/api/inventory'

const { Title, Text } = Typography

/** 手工任务表单值（MOVE/REPLENISH/PICK） */
interface ManualTaskFormValues {
  taskType: 'MOVE' | 'REPLENISH' | 'PICK'
  warehouseId: number
  materialId: number
  batchNo?: string
  qty: number
  fromLocationId?: number
  locationId?: number
  remark?: string
}

/** 完成任务表单值（部分完成/改位） */
interface CompleteFormValues {
  actualQty?: number
  locationId?: number
}

/**
 * WMS 作业任务页：顶部库内 KPI 卡片（任务完成量/人效/及时率/库存准确率）
 * + 任务列表（扫描器自动生成的上架/拣选 + 手工移库/补货），支持领取→开始→完成闭环。
 */
export default function TaskList() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<{
    taskType?: string
    status?: number
    warehouseId?: number
    keyword: string
  }>({ keyword: '' })
  const [pagination, setPagination] = useState({ pageNum: 1, pageSize: 10 })
  const [manualOpen, setManualOpen] = useState(false)
  const [completeTarget, setCompleteTarget] = useState<WmsTaskRow | null>(null)
  const [manualForm] = Form.useForm<ManualTaskFormValues>()
  const [completeForm] = Form.useForm<CompleteFormValues>()

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['inventory'] })

  const { data: warehouses } = useQuery({
    queryKey: ['inventory', 'warehouseOptions'],
    queryFn: fetchWarehouseOptions,
    staleTime: 5 * 60 * 1000,
  })
  const whOptions = (warehouses ?? []).map((w) => ({ label: `${w.code} ${w.name}`, value: w.id }))

  const { data: kpi } = useQuery({
    queryKey: ['inventory', 'wmsKpi'],
    queryFn: () => fetchWmsKpi(),
  })
  const { data: board } = useQuery({
    queryKey: ['inventory', 'wmsBoard', filters.warehouseId],
    queryFn: () => fetchWmsTaskBoard(filters.warehouseId),
  })
  const { data: abcCounts } = useQuery({
    queryKey: ['inventory', 'abcCounts'],
    queryFn: fetchAbcCounts,
  })

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['inventory', 'wmsTasks', filters, pagination],
    queryFn: () =>
      fetchWmsTaskPage({ ...pagination, ...filters, keyword: filters.keyword || undefined }),
    placeholderData: keepPreviousData,
  })

  // 看板：按类型聚合待办数
  const boardByType = useMemo(() => {
    const map = new Map<string, number>()
    for (const row of board ?? []) {
      map.set(row.task_type, (map.get(row.task_type) ?? 0) + Number(row.cnt))
    }
    return map
  }, [board])

  const claimMutation = useMutation({
    mutationFn: (id: number) => claimWmsTask(id),
    onSuccess: () => {
      message.success('已领取，任务进入待执行')
      invalidate()
    },
  })
  const startMutation = useMutation({
    mutationFn: (id: number) => startWmsTask(id),
    onSuccess: () => {
      message.success('任务已开始')
      invalidate()
    },
  })
  const completeMutation = useMutation({
    mutationFn: (values: CompleteFormValues) =>
      completeWmsTask(completeTarget?.id as number, {
        actualQty: values.actualQty,
        locationId: values.locationId,
      }),
    onSuccess: () => {
      message.success('任务完成（库存事务已提交）')
      setCompleteTarget(null)
      completeForm.resetFields()
      invalidate()
    },
  })
  const cancelMutation = useMutation({
    mutationFn: (id: number) => cancelWmsTask(id),
    onSuccess: () => {
      message.success('任务已取消')
      invalidate()
    },
  })
  const manualMutation = useMutation({
    mutationFn: (values: ManualTaskFormValues) =>
      createWmsManualTask({ ...values, batchNo: values.batchNo?.trim() || undefined }),
    onSuccess: () => {
      message.success('任务已创建（待分配）')
      setManualOpen(false)
      manualForm.resetFields()
      invalidate()
    },
  })
  const scanMutation = useMutation({
    mutationFn: triggerWmsScan,
    onSuccess: (r) => message.success(`扫描完成，本轮生成 ${r.created} 个任务`),
  })
  const recalcMutation = useMutation({
    mutationFn: recalcAbc,
    onSuccess: (r) => {
      const parts = r.map((x) => `${x.level}:${x.cnt}`).join(' / ')
      message.success(`ABC 分级重算完成（${parts}）`)
      invalidate()
    },
  })

  const columns: ColumnsType<WmsTaskRow> = [
    { title: '任务号', dataIndex: 'taskNo', width: 140 },
    {
      title: '类型',
      dataIndex: 'taskType',
      width: 76,
      render: (v: string) => <Tag color="blue">{TASK_TYPE_MAP[v] ?? v}</Tag>,
    },
    { title: '仓库', dataIndex: 'warehouseName', width: 110, render: (v: string | null) => v ?? '-' },
    {
      title: '物料',
      key: 'material',
      width: 170,
      render: (_, r) => (
        <Space size={4}>
          <span className="font-mono">{r.materialCode ?? r.materialId}</span>
          <span className="text-xs text-gray-500">{r.materialName ?? ''}</span>
        </Space>
      ),
    },
    {
      title: '批次',
      dataIndex: 'batchNo',
      width: 110,
      render: (v: string) => (v ? <Tag>{v}</Tag> : <Tag>汇总</Tag>),
    },
    { title: '数量', dataIndex: 'qty', width: 90, align: 'right', render: (v: number) => fmtQty(v) },
    {
      title: '库位',
      key: 'location',
      width: 150,
      render: (_, r) =>
        r.taskType === 'MOVE' || r.taskType === 'REPLENISH'
          ? `${r.fromLocationCode ?? '-'} → ${r.locationCode ?? '-'}`
          : (r.locationCode ?? <Tag>未定位</Tag>),
    },
    { title: '来源单号', dataIndex: 'sourceNo', width: 140, render: (v: string | null) => v ?? '-' },
    { title: '执行人', dataIndex: 'assigneeName', width: 90, render: (v: string | null) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) => <StatusTag value={TASK_STATUS_MAP[v] ?? v} />,
    },
    { title: '完成时间', dataIndex: 'finishTime', width: 160, render: (v: string | null) => v ?? '-' },
    {
      title: '操作',
      key: 'actions',
      width: 190,
      fixed: 'right',
      render: (_, record) => (
        <Space size={0}>
          {record.status === 0 && (
            <HasPerm code="inventory:wms:task:edit">
              <Button type="link" size="small" onClick={() => claimMutation.mutate(record.id)}>
                领取
              </Button>
            </HasPerm>
          )}
          {record.status === 10 && (
            <HasPerm code="inventory:wms:task:edit">
              <Button type="link" size="small" onClick={() => startMutation.mutate(record.id)}>
                开始
              </Button>
            </HasPerm>
          )}
          {(record.status === 10 || record.status === 20) && (
            <HasPerm code="inventory:wms:task:audit">
              <Button type="link" size="small" onClick={() => setCompleteTarget(record)}>
                完成
              </Button>
            </HasPerm>
          )}
          {record.status >= 0 && record.status < 30 && (
            <HasPerm code="inventory:wms:task:edit">
              <Popconfirm
                title="确认取消该任务？"
                description="扫描器来源任务取消后不会重复生成"
                onConfirm={() => cancelMutation.mutate(record.id)}
              >
                <Button type="link" size="small" danger>
                  取消
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  const pct = (v: number | null | undefined) =>
    v == null ? '-' : `${(v * 100).toFixed(1)}%`

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        WMS 作业任务
      </Title>

      {/* KPI 卡片行（近 30 天） */}
      <Row gutter={12} style={{ marginBottom: 12 }}>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="完成任务量（近30天）"
              value={kpi?.completedTasks ?? 0}
              prefix={<CheckCircleOutlined style={{ color: '#1668dc' }} />}
            />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="平均耗时（分钟）"
              value={kpi?.avgDurationMinutes ?? 0}
              precision={1}
              prefix={<ClockCircleOutlined style={{ color: '#faad14' }} />}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card size="small">
            <Statistic title="及时率（当日完成）" value={pct(kpi?.onTimeRate)} />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="库存准确率"
              value={pct(kpi?.stockAccuracy)}
              suffix={
                kpi?.stockAccuracy == null ? (
                  <Text type="secondary" className="text-xs">
                    （窗口内无盘点）
                  </Text>
                ) : null
              }
            />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small" title="待办看板 / ABC 分级" styles={{ body: { padding: '8px 12px' } }}>
            <Space size={6} wrap>
              {(['PUTAWAY', 'PICK', 'MOVE', 'REPLENISH'] as const)
                .filter((t) => (boardByType.get(t) ?? 0) > 0)
                .map((t) => (
                  <Tag key={t} color="processing">
                    {TASK_TYPE_MAP[t]} {boardByType.get(t)}
                  </Tag>
                ))}
              {(abcCounts ?? []).map((c) => (
                <Tag key={c.level} color={c.level === 'A' ? 'red' : c.level === 'B' ? 'orange' : 'green'}>
                  {c.level} 类 {c.cnt}
                </Tag>
              ))}
              {((board ?? []).length === 0 && (abcCounts ?? []).length === 0) && (
                <Text type="secondary">暂无待办与分级</Text>
              )}
            </Space>
          </Card>
        </Col>
      </Row>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            style={{ width: 110 }}
            placeholder="任务类型"
            allowClear
            options={Object.entries(TASK_TYPE_MAP).map(([value, label]) => ({ value, label }))}
            value={filters.taskType}
            onChange={(v) => {
              setFilters((f) => ({ ...f, taskType: v }))
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <Select
            style={{ width: 110 }}
            placeholder="状态"
            allowClear
            options={Object.entries(TASK_STATUS_MAP).map(([value, label]) => ({ value: Number(value), label }))}
            value={filters.status}
            onChange={(v) => {
              setFilters((f) => ({ ...f, status: v }))
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <Select
            style={{ width: 190 }}
            placeholder="全部仓库"
            allowClear
            showSearch
            optionFilterProp="label"
            options={whOptions}
            value={filters.warehouseId}
            onChange={(v) => {
              setFilters((f) => ({ ...f, warehouseId: v }))
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <Input.Search
            style={{ width: 220 }}
            placeholder="任务号/来源单号/物料"
            allowClear
            onSearch={(v) => {
              setFilters((f) => ({ ...f, keyword: v }))
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <HasPerm code="inventory:wms:task:edit">
            <Button icon={<ReloadOutlined />} loading={scanMutation.isPending} onClick={() => scanMutation.mutate()}>
              立即扫描
            </Button>
            <Button icon={<UserOutlined />} onClick={() => setManualOpen(true)}>
              手工任务
            </Button>
          </HasPerm>
          <HasPerm code="inventory:wms:abc:edit">
            <Popconfirm
              title="重算 ABC 分级？"
              description="按近 90 天出库频次重新划分 A/B/C（旧分级整体替换）"
              onConfirm={() => recalcMutation.mutate()}
            >
              <Button icon={<ThunderboltOutlined />} loading={recalcMutation.isPending}>
                ABC 重算
              </Button>
            </Popconfirm>
          </HasPerm>
        </Space>

        <Table<WmsTaskRow>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          scroll={{ x: 1420 }}
          locale={{ emptyText: '暂无任务（扫描器每 30 秒自动生成，或点「立即扫描」）' }}
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

      {/* 手工任务（移库/补货/手工拣选） */}
      <Modal
        title="手工创建任务"
        open={manualOpen}
        onCancel={() => setManualOpen(false)}
        onOk={() => manualForm.submit()}
        confirmLoading={manualMutation.isPending}
        okText="创建"
        destroyOnClose
      >
        <Form<ManualTaskFormValues>
          form={manualForm}
          layout="vertical"
          initialValues={{ taskType: 'MOVE' }}
          onFinish={(values) => manualMutation.mutate(values)}
        >
          <Form.Item name="taskType" label="任务类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'MOVE', label: '移库（两库位间转移）' },
                { value: 'REPLENISH', label: '补货（存储区→拣选区）' },
                { value: 'PICK', label: '手工拣选（无来源单，账实同扣）' },
              ]}
            />
          </Form.Item>
          <Form.Item name="warehouseId" label="仓库" rules={[{ required: true, message: '请选择仓库' }]}>
            <Select placeholder="选择仓库" options={whOptions} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="materialId" label="物料 ID" rules={[{ required: true, message: '请输入物料 ID' }]}>
            <InputNumber style={{ width: '100%' }} min={1} placeholder="物料 ID（批次物料参与库位作业）" />
          </Form.Item>
          <Form.Item name="batchNo" label="批次号（批次物料必填）">
            <Input maxLength={50} placeholder="批次物料必填；普通物料留空" />
          </Form.Item>
          <Form.Item name="qty" label="数量" rules={[{ required: true, message: '请输入数量' }]}>
            <InputNumber style={{ width: '100%' }} min={0.0001} precision={4} />
          </Form.Item>
          <Form.Item name="fromLocationId" label="调出库位 ID（MOVE/REPLENISH/PICK 必填）">
            <InputNumber style={{ width: '100%' }} min={1} placeholder="库位 ID（base_location.id）" />
          </Form.Item>
          <Form.Item name="locationId" label="目标库位 ID（MOVE/REPLENISH 必填）">
            <InputNumber style={{ width: '100%' }} min={1} placeholder="库位 ID（base_location.id）" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={200} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 完成任务（支持部分数量/改位） */}
      <Drawer
        title={completeTarget ? `完成任务 ${completeTarget.taskNo}` : '完成任务'}
        width={420}
        open={completeTarget != null}
        onClose={() => setCompleteTarget(null)}
        extra={
          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={completeMutation.isPending}
            onClick={() => completeForm.submit()}
          >
            提交完成
          </Button>
        }
      >
        {completeTarget && (
          <Form<CompleteFormValues> form={completeForm} layout="vertical" onFinish={(values) => completeMutation.mutate(values)}>
            <p className="text-sm" style={{ marginBottom: 16 }}>
              {TASK_TYPE_MAP[completeTarget.taskType]} · {completeTarget.materialCode ?? completeTarget.materialId}{' '}
              {completeTarget.batchNo ? `批次 ${completeTarget.batchNo}` : '汇总'} · 任务数量{' '}
              <Text strong>{fmtQty(completeTarget.qty)}</Text> · 库位{' '}
              <Text strong>{completeTarget.locationCode ?? '未定位'}</Text>
            </p>
            <p className="mb-4 text-xs text-gray-500">{completeTarget.recommendReason ?? '完成时按任务数量全额执行'}</p>
            <Form.Item
              name="actualQty"
              label="实际完成数量（留空=全额；小于任务量为部分完成，剩余量留在任务上）"
            >
              <InputNumber style={{ width: '100%' }} min={0.0001} precision={4} placeholder={String(completeTarget.qty)} />
            </Form.Item>
            <Form.Item name="locationId" label="实际库位 ID（现场改位时填写，留空=任务库位）">
              <InputNumber style={{ width: '100%' }} min={1} placeholder="库位 ID（base_location.id）" />
            </Form.Item>
          </Form>
        )}
      </Drawer>
    </div>
  )
}
