import HasPerm from '@/components/HasPerm'
import StatusTag from '@/components/StatusTag'
import { useEffect, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Drawer,
  Empty,
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
import { OrderedListOutlined, PlusOutlined, SendOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  TASK_STATUS_MAP,
  TASK_TYPE_MAP,
  claimWmsTask,
  completeWmsTask,
  completeWmsWave,
  createWmsWave,
  fetchOutboundDocsForWave,
  fetchWavePickPath,
  fetchWarehouseOptions,
  fetchWmsWaveDetail,
  fetchWmsWavePage,
  fmtQty,
  type OutboundDocRow,
  type WmsTaskRow,
  type WmsWaveRow,
} from '@/api/inventory'

const { Title, Text } = Typography

const WAVE_STATUS_MAP: Record<number, string> = {
  0: '建波',
  10: '拣选中',
  20: '已完成',
}

interface WaveCreateFormValues {
  warehouseId: number
  remark?: string
}

/**
 * WMS 波次管理页：出库单选择 → 建波 → 波次详情（拣选路径序任务列表，逐条完成勾选）→ 完成波次。
 */
export default function WaveList() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<{ status?: number; warehouseId?: number; keyword: string }>({
    keyword: '',
  })
  const [pagination, setPagination] = useState({ pageNum: 1, pageSize: 10 })
  const [createOpen, setCreateOpen] = useState(false)
  const [createForm] = Form.useForm<WaveCreateFormValues>()
  const [selectedDocs, setSelectedDocs] = useState<OutboundDocRow[]>([])
  const [detailId, setDetailId] = useState<number | null>(null)

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['inventory'] })

  const { data: warehouses } = useQuery({
    queryKey: ['inventory', 'warehouseOptions'],
    queryFn: fetchWarehouseOptions,
    staleTime: 5 * 60 * 1000,
  })
  const whOptions = (warehouses ?? []).map((w) => ({ label: `${w.code} ${w.name}`, value: w.id }))

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['inventory', 'wmsWaves', filters, pagination],
    queryFn: () =>
      fetchWmsWavePage({ ...pagination, ...filters, keyword: filters.keyword || undefined }),
    placeholderData: keepPreviousData,
  })

  const { data: waveDetail } = useQuery({
    queryKey: ['inventory', 'wmsWaveDetail', detailId],
    queryFn: () => fetchWmsWaveDetail(detailId as number),
    enabled: detailId != null,
  })
  const { data: pickPath, isLoading: pathLoading } = useQuery({
    queryKey: ['inventory', 'wavePickPath', detailId],
    queryFn: () => fetchWavePickPath(detailId as number),
    enabled: detailId != null,
  })

  // 建波弹窗内的出库单清单（随仓库选择联动）
  const createWarehouseId = Form.useWatch('warehouseId', createForm)
  const { data: outboundDocs } = useQuery({
    queryKey: ['inventory', 'outboundDocs', createWarehouseId],
    queryFn: () => fetchOutboundDocsForWave(createWarehouseId),
    enabled: createOpen,
  })

  const createMutation = useMutation({
    mutationFn: (values: WaveCreateFormValues) =>
      createWmsWave({
        warehouseId: values.warehouseId,
        remark: values.remark,
        docs: selectedDocs.map((d) => ({ docId: d.docId, sourceType: d.sourceType })),
      }),
    onSuccess: () => {
      message.success('波次已创建，拣选任务已生成并入波')
      setCreateOpen(false)
      setSelectedDocs([])
      createForm.resetFields()
      invalidate()
    },
  })

  const completeWaveMutation = useMutation({
    mutationFn: (id: number) => completeWmsWave(id),
    onSuccess: () => {
      message.success('波次已完成')
      invalidate()
    },
  })
  const claimMutation = useMutation({
    mutationFn: (id: number) => claimWmsTask(id),
    onSuccess: () => {
      message.success('已领取')
      invalidate()
    },
  })
  const completeTaskMutation = useMutation({
    mutationFn: (id: number) => completeWmsTask(id),
    onSuccess: () => {
      message.success('任务完成')
      invalidate()
    },
  })

  const columns: ColumnsType<WmsWaveRow> = [
    { title: '波次号', dataIndex: 'waveNo', width: 150 },
    { title: '仓库', dataIndex: 'warehouseName', width: 120, render: (v: string | null) => v ?? '-' },
    {
      title: '进度',
      key: 'progress',
      width: 120,
      render: (_, r) => (
        <span>
          <Text strong>{r.doneCount}</Text> / {r.taskCount} 任务
        </span>
      ),
    },
    { title: '总数量', dataIndex: 'totalQty', width: 100, align: 'right', render: (v: number) => fmtQty(v) },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) => <StatusTag value={WAVE_STATUS_MAP[v] ?? v} />,
    },
    { title: '完成时间', dataIndex: 'finishTime', width: 165, render: (v: string | null) => v ?? '-' },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v: string | null) => v ?? '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_, record) => (
        <Space size={0}>
          <Button type="link" size="small" icon={<OrderedListOutlined />} onClick={() => setDetailId(record.id)}>
            拣选路径
          </Button>
          {record.status !== 20 && record.doneCount === record.taskCount && record.taskCount > 0 && (
            <HasPerm code="inventory:wms:wave:audit">
              <Popconfirm
                title="完成该波次？"
                description="波内任务已全部完成，关波后不可再操作"
                onConfirm={() => completeWaveMutation.mutate(record.id)}
              >
                <Button type="link" size="small">
                  完成波次
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  const pathColumns: ColumnsType<WmsTaskRow> = [
    {
      title: '#',
      key: 'seq',
      width: 44,
      render: (_, __, index) => <Text strong>{index + 1}</Text>,
    },
    {
      title: '库位',
      key: 'location',
      width: 110,
      render: (_, r) => r.locationCode ?? <Tag>未定位</Tag>,
    },
    { title: '任务号', dataIndex: 'taskNo', width: 130 },
    { title: '物料', dataIndex: 'materialCode', width: 120, render: (v: string | null) => v ?? '-' },
    { title: '批次', dataIndex: 'batchNo', width: 100, render: (v: string) => (v ? <Tag>{v}</Tag> : '-') },
    { title: '数量', dataIndex: 'qty', width: 90, align: 'right', render: (v: number) => fmtQty(v) },
    { title: '来源单号', dataIndex: 'sourceNo', width: 135, render: (v: string | null) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 85,
      render: (v: number) => <StatusTag value={TASK_STATUS_MAP[v] ?? v} />,
    },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      render: (_, r) => (
        <Space size={0}>
          {r.status === 0 && (
            <HasPerm code="inventory:wms:task:edit">
              <Button type="link" size="small" onClick={() => claimMutation.mutate(r.id)}>
                领取
              </Button>
            </HasPerm>
          )}
          {(r.status === 10 || r.status === 20) && (
            <HasPerm code="inventory:wms:task:audit">
              <Popconfirm
                title="完成该拣选任务？"
                description="按任务数量全额执行（出库账面已在出库单审核时扣减）"
                onConfirm={() => completeTaskMutation.mutate(r.id)}
              >
                <Button type="link" size="small">
                  完成
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  useEffect(() => {
    // 建波弹窗关闭时清空选择，避免脏数据带入下次
    if (!createOpen) setSelectedDocs([])
  }, [createOpen])

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        WMS 波次管理
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            style={{ width: 110 }}
            placeholder="状态"
            allowClear
            options={Object.entries(WAVE_STATUS_MAP).map(([value, label]) => ({ value: Number(value), label }))}
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
            style={{ width: 200 }}
            placeholder="波次号搜索"
            allowClear
            onSearch={(v) => {
              setFilters((f) => ({ ...f, keyword: v }))
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <HasPerm code="inventory:wms:wave:edit">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              新建波次
            </Button>
          </HasPerm>
        </Space>

        <Table<WmsWaveRow>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          scroll={{ x: 1080 }}
          locale={{ emptyText: '暂无波次（选多张已审核出库单合并拣选）' }}
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

      {/* 建波：选仓库 → 勾选已审核出库单 → 生成拣选任务并入波 */}
      <Modal
        title="新建波次"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        confirmLoading={createMutation.isPending}
        okText="建波"
        okButtonProps={{ disabled: selectedDocs.length === 0 }}
        destroyOnClose
        width={640}
      >
        <Form<WaveCreateFormValues> form={createForm} layout="vertical" onFinish={(values) => createMutation.mutate(values)}>
          <Form.Item name="warehouseId" label="仓库" rules={[{ required: true, message: '请选择仓库' }]}>
            <Select placeholder="选择仓库（出库单清单随仓库联动）" options={whOptions} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item label="选择已审核出库单（生成拣选任务并入波）" required>
            {createWarehouseId == null ? (
              <Text type="secondary">请先选择仓库</Text>
            ) : (
              <Table<OutboundDocRow>
                rowKey={(r) => `${r.sourceType}-${r.docId}`}
                size="small"
                dataSource={outboundDocs ?? []}
                loading={outboundDocs == null}
                rowSelection={{
                  selectedRowKeys: selectedDocs.map((d) => `${d.sourceType}-${d.docId}`),
                  onChange: (_, rows) => setSelectedDocs(rows),
                  getCheckboxProps: (r) => ({ disabled: r.warehouseId !== createWarehouseId }),
                }}
                pagination={false}
                scroll={{ y: 260 }}
                locale={{ emptyText: <Empty description="近 7 天无待建波的已审核出库单" /> }}
                columns={[
                  { title: '出库单号', dataIndex: 'docNo', width: 150 },
                  {
                    title: '类型',
                    dataIndex: 'sourceType',
                    width: 90,
                    render: (v: string) => (v === 'SAL_OUT' ? '销售出库' : '其他出库'),
                  },
                  { title: '行数', dataIndex: 'lineCount', width: 60, align: 'right' },
                  { title: '数量', dataIndex: 'totalQty', width: 90, align: 'right', render: (v: number) => fmtQty(v) },
                  { title: '审核时间', dataIndex: 'auditTime', width: 160, render: (v: string | null) => v ?? '-' },
                ]}
              />
            )}
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={200} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 波次详情：拣选路径序任务列表 */}
      <Drawer
        title={waveDetail ? `波次 ${waveDetail.waveNo}（拣选路径）` : '波次详情'}
        width={960}
        open={detailId != null}
        onClose={() => setDetailId(null)}
        extra={
          waveDetail &&
          waveDetail.status !== 20 &&
          waveDetail.doneCount === waveDetail.taskCount &&
          waveDetail.taskCount > 0 && (
            <HasPerm code="inventory:wms:wave:audit">
              <Popconfirm
                title="完成该波次？"
                description="波内任务已全部完成，关波后不可再操作"
                onConfirm={() => completeWaveMutation.mutate(waveDetail.id)}
              >
                <Button type="primary" danger icon={<SendOutlined />} loading={completeWaveMutation.isPending}>
                  完成波次
                </Button>
              </Popconfirm>
            </HasPerm>
          )
        }
      >
        {waveDetail && (
          <>
            <div className="mb-3">
              <Space size={16}>
                <span>
                  进度 <Text strong>{waveDetail.doneCount}</Text> / {waveDetail.taskCount} 任务
                </span>
                <span>
                  总数量 <Text strong>{fmtQty(waveDetail.totalQty)}</Text>
                </span>
                <StatusTag value={WAVE_STATUS_MAP[waveDetail.status] ?? waveDetail.status} />
              </Space>
              <p className="mt-2 text-xs text-gray-500">
                拣选路径 = 按库位编码升序（最简路径）；任务类型 {TASK_TYPE_MAP.PICK}，出库账面已在出库单审核时扣减。
              </p>
            </div>
            <Table<WmsTaskRow>
              rowKey="id"
              size="small"
              columns={pathColumns}
              dataSource={pickPath ?? []}
              loading={pathLoading}
              pagination={false}
              locale={{ emptyText: '波内无任务' }}
            />
          </>
        )}
      </Drawer>
    </div>
  )
}
