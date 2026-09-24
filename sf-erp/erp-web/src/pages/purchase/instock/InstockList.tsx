import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
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
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import {
  instockApi,
  orderApi,
  type DocPageParams,
  type InstockPreview,
  type PurInstockDetailRow,
  type PurInstockRow,
  type PurInstockSavePayload,
} from '@/api/purchase'
import { reverseDoc } from '@/api/basedata'
import {
  DOC_STATUS_OPTIONS,
  DocStatusTag,
  fmtAmount,
  fmtQty,
  lineAmount,
  optionLabel,
  useSupplierOptions,
  useWarehouseOptions,
} from '../shared'

const { Title, Text } = Typography

/** 可编辑的入库明细行（由选单预填充，实收数量/单价可改） */
interface EditableInstockRow {
  orderDetailId: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  spec: string | null
  unit: string | null
  price: number
  taxRate: number
  orderQty: number
  receivedQty: number
  suggestQty: number
  /** 本次实收（默认=建议数量） */
  qty: number
}

/**
 * 采购入库单页：单据列表 + Drawer 详情 + 选单生成入库单
 * （选择已审核/部分入库的采购订单 -> 带入明细，可改实收数量，禁止超收）。
 */
export default function InstockList() {
  const [keyword, setKeyword] = useState('')
  const [supplierId, setSupplierId] = useState<number | undefined>()
  const [status, setStatus] = useState<number | undefined>()
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [createOpen, setCreateOpen] = useState(false)

  const suppliers = useSupplierOptions()

  const params: DocPageParams = {
    pageNum,
    pageSize,
    keyword: keyword || undefined,
    supplierId,
    status,
    dateBegin: range?.[0]?.format('YYYY-MM-DD'),
    dateEnd: range?.[1]?.format('YYYY-MM-DD'),
  }

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['purchase', 'instock', params],
    queryFn: () => instockApi.page(params),
  })

  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['purchase', 'instock'] })

  const auditMut = useMutation({
    mutationFn: instockApi.audit,
    onSuccess: () => {
      message.success('审核成功：库存/应付已生成')
      invalidate()
    },
  })
  const deleteMut = useMutation({
    mutationFn: instockApi.remove,
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
  })

  /** 红冲已审核入库单：生成负数反向入库单并反向过账（不可撤销） */
  const handleReverse = (row: PurInstockRow) => {
    let reason = ''
    Modal.confirm({
      title: `确认红冲入库单 ${row.instockNo}？`,
      content: (
        <div>
          <p style={{ color: '#cf1322' }}>将生成负数反向入库单并反向过账，操作不可撤销。</p>
          <Input placeholder="红冲原因（选填）" maxLength={200} onChange={(e) => (reason = e.target.value)} />
        </div>
      ),
      okText: '确认红冲',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          const res = await reverseDoc('PUR_INSTOCK', row.id, reason)
          message.success(`红冲成功：已生成反向单 ${res.reverseDocNo}`)
          invalidate()
        } catch {
          /* 错误提示由请求拦截器统一处理 */
        }
      },
    })
  }

  const columns: ColumnsType<PurInstockRow> = [
    { title: '入库单号', dataIndex: 'instockNo', width: 170 },
    { title: '来源订单', dataIndex: 'orderNo', width: 170 },
    { title: '入库日期', dataIndex: 'instockDate', width: 110 },
    { title: '供应商', dataIndex: 'supplierName', width: 160, ellipsis: true },
    { title: '入库仓库', dataIndex: 'warehouseName', width: 130, ellipsis: true },
    {
      title: '价税合计',
      dataIndex: 'totalAmount',
      width: 120,
      align: 'right',
      render: (v: number) => <Text strong>{fmtAmount(v)}</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: PurInstockRow['status']) =>
        Number(v) === 20 ? <Tag color="red">已红冲</Tag> : <DocStatusTag status={v} />,
    },
    { title: '审核时间', dataIndex: 'auditTime', width: 165, render: (v: string | null) => v ?? '-' },
    {
      title: '操作',
      key: 'actions',
      width: 170,
      fixed: 'right',
      render: (_, row) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => setDetailId(row.id)}>
            详情
          </Button>
          {row.status === 0 && (
            <HasPerm code="purchase:instock:audit">
              <Popconfirm
                title="审核后将写入库存并生成应付，确认？"
                onConfirm={() => auditMut.mutate(row.id)}
              >
                <Button type="link" size="small">
                  审核
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 10 && (
            <HasPerm code="purchase:instock:reverse">
              <Button type="link" size="small" danger onClick={() => handleReverse(row)}>
                红冲
              </Button>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="purchase:instock:delete">
            <Popconfirm title="确认删除该草稿入库单？" onConfirm={() => deleteMut.mutate(row.id)}>
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

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        采购入库
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="入库单号/订单号搜索"
            style={{ width: 220 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setKeyword(v)
            }}
          />
          <Select
            placeholder="供应商"
            style={{ width: 200 }}
            allowClear
            showSearch
            optionFilterProp="label"
            options={(suppliers.data ?? []).map((s) => ({ value: s.id, label: optionLabel(s) }))}
            onChange={(v) => {
              setPageNum(1)
              setSupplierId(v)
            }}
          />
          <Select
            placeholder="状态"
            style={{ width: 120 }}
            allowClear
            options={DOC_STATUS_OPTIONS}
            onChange={(v) => {
              setPageNum(1)
              setStatus(v)
            }}
          />
          <DatePicker.RangePicker
            onChange={(v) => {
              setPageNum(1)
              setRange(v as [Dayjs, Dayjs] | null)
            }}
          />
          <HasPerm code="purchase:instock:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              从订单生成入库单
            </Button>
          </HasPerm>
        </Space>

        <Table<PurInstockRow>
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          scroll={{ x: 1200 }}
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
      </Card>

      {detailId != null && <InstockDetailDrawer id={detailId} onClose={() => setDetailId(null)} />}
      {createOpen && <InstockCreateDrawer onClose={() => setCreateOpen(false)} />}
    </div>
  )
}

/** 入库明细列（详情抽屉） */
const DETAIL_COLUMNS: ColumnsType<PurInstockDetailRow> = [
  { title: '物料编码', dataIndex: 'materialCode', width: 120 },
  { title: '物料名称', dataIndex: 'materialName', width: 150, ellipsis: true },
  { title: '规格', dataIndex: 'spec', width: 110, ellipsis: true },
  { title: '单位', dataIndex: 'unit', width: 70 },
  { title: '实收数量', dataIndex: 'qty', width: 100, align: 'right', render: fmtQty },
  { title: '含税单价', dataIndex: 'price', width: 100, align: 'right', render: (v: number) => v.toFixed(4) },
  { title: '税率%', dataIndex: 'taxRate', width: 80, align: 'right' },
  { title: '金额', dataIndex: 'amount', width: 110, align: 'right', render: (v: number) => <Text strong>{fmtAmount(v)}</Text> },
  { title: '已退数量', dataIndex: 'returnedQty', width: 100, align: 'right', render: fmtQty },
  { title: '备注', dataIndex: 'remark', ellipsis: true },
]

/** 入库单详情抽屉 */
function InstockDetailDrawer({ id, onClose }: { id: number; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['purchase', 'instock-detail', id],
    queryFn: () => instockApi.get(id),
  })

  return (
    <Drawer title={`采购入库单详情 ${data?.instockNo ?? ''}`} width={1040} open onClose={onClose}>
      {isLoading || !data ? (
        <Card loading style={{ border: 'none' }} />
      ) : (
        <>
          <Descriptions bordered size="small" column={3}>
            <Descriptions.Item label="入库单号">{data.instockNo}</Descriptions.Item>
            <Descriptions.Item label="状态"><DocStatusTag status={data.status} /></Descriptions.Item>
            <Descriptions.Item label="入库日期">{data.instockDate}</Descriptions.Item>
            <Descriptions.Item label="来源订单">{data.orderNo ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="供应商">{data.supplierName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="入库仓库">{data.warehouseName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="价税合计">
              <Text strong style={{ color: '#1668dc' }}>{fmtAmount(data.totalAmount)}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="审核时间">{data.auditTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="备注">{data.remark ?? '-'}</Descriptions.Item>
          </Descriptions>

          <Title level={5} style={{ margin: '16px 0 8px' }}>
            入库明细
          </Title>
          <Table<PurInstockDetailRow>
            rowKey="id"
            size="small"
            columns={DETAIL_COLUMNS}
            dataSource={data.details ?? []}
            pagination={false}
            scroll={{ x: 1000 }}
          />
        </>
      )}
    </Drawer>
  )
}

/** 选单生成入库单抽屉：选已审核/部分入库订单 -> 预填明细 -> 改实收数量 -> 提交草稿 */
function InstockCreateDrawer({ onClose }: { onClose: () => void }) {
  const [preview, setPreview] = useState<InstockPreview | null>(null)
  const [rows, setRows] = useState<EditableInstockRow[]>([])
  const [warehouseId, setWarehouseId] = useState<number | undefined>()
  const [instockDate, setInstockDate] = useState<Dayjs>(dayjs())
  const [remark, setRemark] = useState('')
  const [loadingPreview, setLoadingPreview] = useState(false)

  const warehouses = useWarehouseOptions()
  const queryClient = useQueryClient()

  // 可选来源订单：已审核(10) + 部分入库(20)，各取第一页（ERP 待入库订单量级可控）
  const auditedOrders = useQuery({
    queryKey: ['purchase', 'orders', 'select', 10],
    queryFn: () => orderApi.page({ pageNum: 1, pageSize: 50, status: 10 }),
  })
  const partialOrders = useQuery({
    queryKey: ['purchase', 'orders', 'select', 20],
    queryFn: () => orderApi.page({ pageNum: 1, pageSize: 50, status: 20 }),
  })
  const orderOptions = [
    ...(auditedOrders.data?.records ?? []),
    ...(partialOrders.data?.records ?? []),
  ].map((o) => ({
    value: o.id,
    label: `${o.orderNo}（${o.supplierName ?? '-'} / ${fmtAmount(o.totalAmount)}）`,
  }))

  /** 选单：拉取预填充数据，实收数量默认=建议数量（剩余可入库） */
  const pickOrder = async (orderId: number) => {
    setLoadingPreview(true)
    try {
      const p = await instockApi.previewFromOrder(orderId)
      setPreview(p)
      setWarehouseId(p.warehouseId ?? undefined)
      setRows(p.details.map((d) => ({ ...d, qty: d.suggestQty })))
    } catch {
      /* 拦截器已提示 */
    } finally {
      setLoadingPreview(false)
    }
  }

  const total = rows.reduce((sum, r) => sum + lineAmount(r.qty, r.price), 0)

  const createMut = useMutation({
    mutationFn: (payload: PurInstockSavePayload) => instockApi.create(payload),
    onSuccess: () => {
      message.success('入库单已生成（草稿），审核后写入库存')
      queryClient.invalidateQueries({ queryKey: ['purchase', 'instock'] })
      onClose()
    },
  })

  const submit = () => {
    if (!preview) return
    if (warehouseId == null) {
      message.warning('请选择入库仓库')
      return
    }
    const details = rows.filter((r) => r.qty > 0)
    if (details.length === 0) {
      message.warning('请至少录入一行实收数量大于 0 的明细')
      return
    }
    createMut.mutate({
      orderId: preview.orderId,
      supplierId: preview.supplierId,
      warehouseId,
      instockDate: instockDate.format('YYYY-MM-DD'),
      remark: remark || undefined,
      details: details.map((r) => ({
        orderDetailId: r.orderDetailId,
        materialId: r.materialId,
        qty: r.qty,
        price: r.price,
        taxRate: r.taxRate,
      })),
    })
  }

  const editColumns: ColumnsType<EditableInstockRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 110 },
    { title: '物料名称', dataIndex: 'materialName', width: 140, ellipsis: true },
    { title: '规格', dataIndex: 'spec', width: 100, ellipsis: true },
    { title: '单位', dataIndex: 'unit', width: 60 },
    { title: '订单数量', dataIndex: 'orderQty', width: 90, align: 'right', render: fmtQty },
    { title: '已入库', dataIndex: 'receivedQty', width: 90, align: 'right', render: fmtQty },
    {
      title: '本次实收',
      dataIndex: 'qty',
      width: 130,
      render: (_, row, idx) => (
        <InputNumber
          min={0.0001}
          max={row.suggestQty}
          precision={4}
          value={row.qty}
          style={{ width: '100%' }}
          onChange={(v) =>
            setRows((rs) => rs.map((r, i) => (i === idx ? { ...r, qty: v ?? 0 } : r)))
          }
        />
      ),
    },
    {
      title: '含税单价',
      dataIndex: 'price',
      width: 120,
      render: (_, row, idx) => (
        <InputNumber
          min={0}
          precision={4}
          value={row.price}
          style={{ width: '100%' }}
          onChange={(v) => setRows((rs) => rs.map((r, i) => (i === idx ? { ...r, price: v ?? 0 } : r)))}
        />
      ),
    },
    {
      title: '金额',
      width: 100,
      align: 'right',
      render: (_, row) => <Text>{fmtAmount(lineAmount(row.qty, row.price))}</Text>,
    },
  ]

  return (
    <Drawer title="从采购订单生成入库单" width={1120} open onClose={onClose}
      footer={
        <Space style={{ display: 'flex', justifyContent: 'space-between' }}>
          <Text>
            价税合计：<Text strong style={{ color: '#1668dc', fontSize: 16 }}>{fmtAmount(total)}</Text>
          </Text>
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button type="primary" disabled={!preview} loading={createMut.isPending} onClick={submit}>
              生成入库单（草稿）
            </Button>
          </Space>
        </Space>
      }
    >
      <Space wrap style={{ marginBottom: 16, width: '100%' }}>
        <Select
          placeholder="选择已审核的采购订单"
          style={{ width: 360 }}
          showSearch
          optionFilterProp="label"
          loading={auditedOrders.isLoading || partialOrders.isLoading}
          options={orderOptions}
          onChange={(v) => void pickOrder(v)}
        />
        <Select
          placeholder="入库仓库"
          style={{ width: 220 }}
          showSearch
          optionFilterProp="label"
          value={warehouseId}
          options={(warehouses.data ?? []).map((w) => ({ value: w.id, label: optionLabel(w) }))}
          onChange={setWarehouseId}
        />
        <DatePicker value={instockDate} onChange={(d) => setInstockDate(d ?? dayjs())} />
      </Space>

      {loadingPreview && <Card loading style={{ border: 'none' }} />}

      {!loadingPreview && preview && (
        <>
          <Descriptions bordered size="small" column={3} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="采购订单">{preview.orderNo}</Descriptions.Item>
            <Descriptions.Item label="供应商">{preview.supplierName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="订单日期">{preview.orderDate}</Descriptions.Item>
          </Descriptions>
          {rows.length === 0 ? (
            <Text type="warning">该订单所有明细已全部入库，无可入库数量</Text>
          ) : (
            <Table<EditableInstockRow>
              rowKey="orderDetailId"
              size="small"
              columns={editColumns}
              dataSource={rows}
              pagination={false}
              scroll={{ x: 1000 }}
            />
          )}
          <Input.TextArea
            rows={2}
            style={{ marginTop: 12 }}
            maxLength={500}
            placeholder="入库备注"
            value={remark}
            onChange={(e) => setRemark(e.target.value)}
          />
        </>
      )}

      {!loadingPreview && !preview && (
        <Card style={{ border: 'none' }}>
          <Text type="secondary">请先选择来源采购订单（仅已审核/部分入库状态可选）</Text>
        </Card>
      )}
    </Drawer>
  )
}
