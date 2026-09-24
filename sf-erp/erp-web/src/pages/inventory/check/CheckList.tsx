import HasPerm from '@/components/HasPerm'
import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import {
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
  Tag,
  Typography,
  message,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  completeCheck,
  createCheck,
  deleteCheck,
  fetchCheckDetail,
  fetchCheckPage,
  fetchWarehouseOptions,
  fmtQty,
  saveCheckActual,
  type CheckDocDetail,
  type CheckRow,
} from '@/api/inventory'

const { Title, Text } = Typography

/** 新建盘点表单值 */
interface CheckCreateFormValues {
  warehouseId: number
  remark?: string
}

const LIST_COLUMNS: ColumnsType<CheckRow> = [
  { title: '盘点单号', dataIndex: 'no', width: 150 },
  { title: '仓库', dataIndex: 'warehouseName', width: 130, render: (v: string | null) => v ?? '-' },
  {
    title: '盘点进度',
    key: 'progress',
    width: 130,
    render: (_, r) => (
      <span>
        已盘 <Text strong>{r.actualCount}</Text> / {r.lineCount} 行
      </span>
    ),
  },
  {
    title: '差异行数',
    dataIndex: 'diffCount',
    width: 90,
    align: 'right',
    render: (v: number) => (v > 0 ? <Tag style={{ marginInlineEnd: 0 }} color="warning">{v}</Tag> : v),
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 100,
    render: (v: number) =>
      v === 1 ? <Tag color="success">已完成</Tag> : <Tag color="processing">盘点中</Tag>,
  },
  { title: '完成时间', dataIndex: 'completeTime', width: 170, render: (v: string | null) => v ?? '-' },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
]

/**
 * 库存盘点页：按仓库生成库存快照 → 录实盘（差异实时计算）→ 完成盘点自动调平。
 */
export default function CheckList() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<{ status?: number; warehouseId?: number; keyword: string }>({
    keyword: '',
  })
  const [pagination, setPagination] = useState({ pageNum: 1, pageSize: 10 })
  const [createOpen, setCreateOpen] = useState(false)
  const [createForm] = Form.useForm<CheckCreateFormValues>()
  const [detailId, setDetailId] = useState<number | null>(null)
  // 实盘编辑态 {明细行id: 实盘数量}；保存后随服务端刷新重置
  const [edits, setEdits] = useState<Record<number, number>>({})

  const { data: warehouses } = useQuery({
    queryKey: ['inventory', 'warehouseOptions'],
    queryFn: fetchWarehouseOptions,
    staleTime: 5 * 60 * 1000,
  })
  const whOptions = (warehouses ?? []).map((w) => ({ label: `${w.code} ${w.name}`, value: w.id }))

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['inventory', 'checks', filters, pagination],
    queryFn: () =>
      fetchCheckPage({ ...pagination, ...filters, keyword: filters.keyword || undefined }),
    placeholderData: keepPreviousData,
  })

  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ['inventory', 'checks', 'detail', detailId],
    queryFn: () => fetchCheckDetail(detailId as number),
    enabled: detailId != null,
  })

  // 详情变化时重置本地编辑态为服务端已保存的实盘值
  useEffect(() => {
    if (detail) {
      const initial: Record<number, number> = {}
      for (const line of detail.lines) {
        if (line.actualQty != null) initial[line.id] = line.actualQty
      }
      setEdits(initial)
    }
  }, [detail])

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['inventory'] })

  const createMutation = useMutation({
    mutationFn: (values: CheckCreateFormValues) => createCheck(values),
    onSuccess: () => {
      message.success('盘点单已创建，库存快照已生成')
      setCreateOpen(false)
      createForm.resetFields()
      invalidate()
    },
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      const lines = Object.entries(edits)
        .filter(([, v]) => v != null && !Number.isNaN(v))
        .map(([detailIdStr, actualQty]) => ({ detailId: Number(detailIdStr), actualQty }))
      return saveCheckActual(detailId as number, lines)
    },
    onSuccess: () => {
      message.success('实盘数量已保存')
      invalidate()
    },
  })

  const completeMutation = useMutation({
    mutationFn: () => completeCheck(detailId as number),
    onSuccess: (res) => {
      message.success(`盘点完成，差异调平 ${res.adjusted} 行`)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteCheck(id),
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
  })

  const columns: ColumnsType<CheckRow> = [
    ...LIST_COLUMNS,
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => setDetailId(record.id)}>
            {record.status === 0 ? '录入实盘' : '详情'}
          </Button>
          {record.status === 0 && (
            <HasPerm code="inventory:check:delete">
            <Popconfirm title="确认删除该盘点单？" onConfirm={() => deleteMutation.mutate(record.id)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  const open = detail != null && detail.status === 0

  const detailColumns: ColumnsType<CheckDocDetail['lines'][number]> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 110, render: (v: string | null) => v ?? '-' },
    { title: '物料名称', dataIndex: 'materialName', ellipsis: true, render: (v: string | null) => v ?? '-' },
    { title: '账面数量', dataIndex: 'bookQty', width: 100, align: 'right', render: (v: number) => fmtQty(v) },
    {
      title: '实盘数量',
      dataIndex: 'actualQty',
      width: 130,
      render: (_, line) =>
        open ? (
          <InputNumber
            size="small"
            min={0}
            precision={4}
            style={{ width: '100%' }}
            value={line.id in edits ? edits[line.id] : (line.actualQty ?? undefined)}
            onChange={(v) => setEdits((prev) => ({ ...prev, [line.id]: v as number }))}
            placeholder="未盘点"
          />
        ) : (
          fmtQty(line.actualQty)
        ),
    },
    {
      title: '差异',
      dataIndex: 'diffQty',
      width: 100,
      align: 'right',
      render: (_, line) => {
        const actual = line.id in edits ? edits[line.id] : line.actualQty
        if (actual == null) return '-'
        const diff = actual - line.bookQty
        if (Math.abs(diff) < 1e-9) return <Tag style={{ marginInlineEnd: 0 }}>平</Tag>
        return (
          <span style={{ color: diff > 0 ? '#389e0d' : '#cf1322' }}>
            {diff > 0 ? '+' : ''}
            {fmtQty(diff)}
          </span>
        )
      },
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        库存盘点
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            style={{ width: 120 }}
            placeholder="状态"
            allowClear
            options={[
              { label: '盘点中', value: 0 },
              { label: '已完成', value: 1 },
            ]}
            value={filters.status}
            onChange={(v) => {
              setFilters((f) => ({ ...f, status: v }))
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <Select
            style={{ width: 200 }}
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
            placeholder="盘点单号搜索"
            allowClear
            onSearch={(v) => {
              setFilters((f) => ({ ...f, keyword: v }))
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <HasPerm code="inventory:check:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              新建盘点单
            </Button>
          </HasPerm>
        </Space>

        <Table<CheckRow>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          locale={{ emptyText: '暂无盘点单' }}
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

      {/* 新建盘点 */}
      <Modal
        title="新建盘点单"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        confirmLoading={createMutation.isPending}
        okText="生成快照"
        destroyOnClose
      >
        <Form<CheckCreateFormValues> form={createForm} layout="vertical" onFinish={(values) => createMutation.mutate(values)}>
          <Form.Item
            name="warehouseId"
            label="盘点仓库"
            rules={[{ required: true, message: '请选择仓库' }]}
          >
            <Select placeholder="选择仓库" options={whOptions} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={200} placeholder="盘点原因/说明（可选）" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 盘点详情/录实盘 */}
      <Drawer
        title={detail ? `盘点单 ${detail.no}` : '盘点单详情'}
        width={760}
        open={detailId != null}
        onClose={() => setDetailId(null)}
        loading={detailLoading}
        extra={
          open && (
            <Space>
              <Button type="primary" loading={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
                保存实盘
              </Button>
              <HasPerm code="inventory:check:audit">
                <Popconfirm
                  title="确认完成盘点？"
                  description="差异行将自动生成盘点调整流水并调平库存"
                  onConfirm={() => completeMutation.mutate()}
                >
                  <Button danger type="primary" loading={completeMutation.isPending}>
                    完成盘点(调平)
                  </Button>
                </Popconfirm>
              </HasPerm>
            </Space>
          )
        }
      >
        {detail && (
          <>
            <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="状态">
                {detail.status === 1 ? (
                  <Tag color="success">已完成</Tag>
                ) : (
                  <Tag color="processing">盘点中</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="完成时间">{detail.completeTime ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>
                {detail.remark ?? '-'}
              </Descriptions.Item>
            </Descriptions>
            <Table
              rowKey="id"
              size="small"
              columns={detailColumns}
              dataSource={detail.lines}
              pagination={false}
              locale={{ emptyText: '该仓库生成快照时无库存记录' }}
            />
          </>
        )}
      </Drawer>
    </div>
  )
}
