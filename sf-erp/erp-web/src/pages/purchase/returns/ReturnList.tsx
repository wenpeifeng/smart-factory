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
  returnApi,
  type DocPageParams,
  type PurReturnDetailRow,
  type PurReturnRow,
  type PurReturnSavePayload,
  type ReturnPreview,
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
} from '../shared'

const { Title, Text } = Typography

/** 可编辑的退货明细行（由选单预填充，退货数量可改但不超可退数量） */
interface EditableReturnRow {
  instockDetailId: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  spec: string | null
  unit: string | null
  price: number
  taxRate: number
  instockQty: number
  returnedQty: number
  suggestQty: number
  /** 本次退货（默认=建议数量） */
  qty: number
}

/**
 * 采购退货单页：单据列表 + Drawer 详情 + 选单退货
 * （选择已审核入库单 -> 带入明细，可改退货数量，审核时反向库存事务）。
 */
export default function ReturnList() {
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
    queryKey: ['purchase', 'returns', params],
    queryFn: () => returnApi.page(params),
  })

  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['purchase', 'returns'] })

  const auditMut = useMutation({
    mutationFn: returnApi.audit,
    onSuccess: () => {
      message.success('审核成功：库存已反向冲减')
      invalidate()
    },
  })
  const deleteMut = useMutation({
    mutationFn: returnApi.remove,
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
  })

  /** 红冲已审核退货单：生成负数反向退货单并反向过账（不可撤销） */
  const handleReverse = (row: PurReturnRow) => {
    let reason = ''
    Modal.confirm({
      title: `确认红冲退货单 ${row.returnNo}？`,
      content: (
        <div>
          <p style={{ color: '#cf1322' }}>将生成负数反向退货单并反向过账，操作不可撤销。</p>
          <Input placeholder="红冲原因（选填）" maxLength={200} onChange={(e) => (reason = e.target.value)} />
        </div>
      ),
      okText: '确认红冲',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          const res = await reverseDoc('PUR_RETURN', row.id, reason)
          message.success(`红冲成功：已生成反向单 ${res.reverseDocNo}`)
          invalidate()
        } catch {
          /* 错误提示由请求拦截器统一处理 */
        }
      },
    })
  }

  const columns: ColumnsType<PurReturnRow> = [
    { title: '退货单号', dataIndex: 'returnNo', width: 170 },
    { title: '来源入库单', dataIndex: 'instockNo', width: 170 },
    { title: '退货日期', dataIndex: 'returnDate', width: 110 },
    { title: '供应商', dataIndex: 'supplierName', width: 160, ellipsis: true },
    { title: '退货仓库', dataIndex: 'warehouseName', width: 130, ellipsis: true },
    {
      title: '退货金额',
      dataIndex: 'totalAmount',
      width: 120,
      align: 'right',
      render: (v: number) => <Text strong>{fmtAmount(v)}</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: PurReturnRow['status']) =>
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
            <HasPerm code="purchase:return:audit">
              <Popconfirm
                title="审核将扣减库存（禁止负库存），确认？"
                onConfirm={() => auditMut.mutate(row.id)}
              >
                <Button type="link" size="small">
                  审核
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 10 && (
            <HasPerm code="purchase:return:reverse">
              <Button type="link" size="small" danger onClick={() => handleReverse(row)}>
                红冲
              </Button>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="purchase:return:delete">
            <Popconfirm title="确认删除该草稿退货单？" onConfirm={() => deleteMut.mutate(row.id)}>
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
        采购退货
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="退货单号/入库单号搜索"
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
          <HasPerm code="purchase:return:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              从入库单退货
            </Button>
          </HasPerm>
        </Space>

        <Table<PurReturnRow>
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

      {detailId != null && <ReturnDetailDrawer id={detailId} onClose={() => setDetailId(null)} />}
      {createOpen && <ReturnCreateDrawer onClose={() => setCreateOpen(false)} />}
    </div>
  )
}

/** 退货明细列（详情抽屉） */
const DETAIL_COLUMNS: ColumnsType<PurReturnDetailRow> = [
  { title: '物料编码', dataIndex: 'materialCode', width: 120 },
  { title: '物料名称', dataIndex: 'materialName', width: 150, ellipsis: true },
  { title: '规格', dataIndex: 'spec', width: 110, ellipsis: true },
  { title: '单位', dataIndex: 'unit', width: 70 },
  { title: '退货数量', dataIndex: 'qty', width: 100, align: 'right', render: fmtQty },
  { title: '含税单价', dataIndex: 'price', width: 100, align: 'right', render: (v: number) => v.toFixed(4) },
  { title: '税率%', dataIndex: 'taxRate', width: 80, align: 'right' },
  { title: '金额', dataIndex: 'amount', width: 110, align: 'right', render: (v: number) => <Text strong>{fmtAmount(v)}</Text> },
  { title: '备注', dataIndex: 'remark', ellipsis: true },
]

/** 退货单详情抽屉 */
function ReturnDetailDrawer({ id, onClose }: { id: number; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['purchase', 'return-detail', id],
    queryFn: () => returnApi.get(id),
  })

  return (
    <Drawer title={`采购退货单详情 ${data?.returnNo ?? ''}`} width={1000} open onClose={onClose}>
      {isLoading || !data ? (
        <Card loading style={{ border: 'none' }} />
      ) : (
        <>
          <Descriptions bordered size="small" column={3}>
            <Descriptions.Item label="退货单号">{data.returnNo}</Descriptions.Item>
            <Descriptions.Item label="状态"><DocStatusTag status={data.status} /></Descriptions.Item>
            <Descriptions.Item label="退货日期">{data.returnDate}</Descriptions.Item>
            <Descriptions.Item label="来源入库单">{data.instockNo ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="供应商">{data.supplierName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="退货仓库">{data.warehouseName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="退货金额">
              <Text strong style={{ color: '#fa8c16' }}>{fmtAmount(data.totalAmount)}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="审核时间">{data.auditTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="备注">{data.remark ?? '-'}</Descriptions.Item>
          </Descriptions>

          <Title level={5} style={{ margin: '16px 0 8px' }}>
            退货明细
          </Title>
          <Table<PurReturnDetailRow>
            rowKey="id"
            size="small"
            columns={DETAIL_COLUMNS}
            dataSource={data.details ?? []}
            pagination={false}
            scroll={{ x: 900 }}
          />
        </>
      )}
    </Drawer>
  )
}

/** 选单退货抽屉：选已审核入库单 -> 预填明细 -> 改退货数量 -> 提交草稿 */
function ReturnCreateDrawer({ onClose }: { onClose: () => void }) {
  const [preview, setPreview] = useState<ReturnPreview | null>(null)
  const [rows, setRows] = useState<EditableReturnRow[]>([])
  const [returnDate, setReturnDate] = useState<Dayjs>(dayjs())
  const [remark, setRemark] = useState('')
  const [loadingPreview, setLoadingPreview] = useState(false)

  const queryClient = useQueryClient()

  // 可选来源入库单：已审核（其库存已落账，退货才有冲减依据）
  const auditedInstocks = useQuery({
    queryKey: ['purchase', 'instock', 'select', 10],
    queryFn: () => instockApi.page({ pageNum: 1, pageSize: 50, status: 10 }),
  })
  const instockOptions = (auditedInstocks.data?.records ?? []).map((i) => ({
    value: i.id,
    label: `${i.instockNo}（${i.supplierName ?? '-'} / ${fmtAmount(i.totalAmount)}）`,
  }))

  /** 选单：拉取预填充数据，退货数量默认=建议数量（剩余可退） */
  const pickInstock = async (instockId: number) => {
    setLoadingPreview(true)
    try {
      const p = await returnApi.previewFromInstock(instockId)
      setPreview(p)
      setRows(p.details.map((d) => ({ ...d, qty: d.suggestQty })))
    } catch {
      /* 拦截器已提示 */
    } finally {
      setLoadingPreview(false)
    }
  }

  const total = rows.reduce((sum, r) => sum + lineAmount(r.qty, r.price), 0)

  const createMut = useMutation({
    mutationFn: (payload: PurReturnSavePayload) => returnApi.create(payload),
    onSuccess: () => {
      message.success('退货单已生成（草稿），审核后扣减库存')
      queryClient.invalidateQueries({ queryKey: ['purchase', 'returns'] })
      onClose()
    },
  })

  const submit = () => {
    if (!preview) return
    const details = rows.filter((r) => r.qty > 0)
    if (details.length === 0) {
      message.warning('请至少录入一行退货数量大于 0 的明细')
      return
    }
    createMut.mutate({
      instockId: preview.instockId,
      supplierId: preview.supplierId,
      warehouseId: preview.warehouseId!,
      returnDate: returnDate.format('YYYY-MM-DD'),
      remark: remark || undefined,
      details: details.map((r) => ({
        instockDetailId: r.instockDetailId,
        materialId: r.materialId,
        qty: r.qty,
        price: r.price,
        taxRate: r.taxRate,
      })),
    })
  }

  const editColumns: ColumnsType<EditableReturnRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 120 },
    { title: '物料名称', dataIndex: 'materialName', width: 150, ellipsis: true },
    { title: '规格', dataIndex: 'spec', width: 110, ellipsis: true },
    { title: '单位', dataIndex: 'unit', width: 70 },
    { title: '入库数量', dataIndex: 'instockQty', width: 95, align: 'right', render: fmtQty },
    { title: '已退', dataIndex: 'returnedQty', width: 85, align: 'right', render: fmtQty },
    {
      title: '本次退货',
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
    <Drawer title="从入库单生成退货单" width={1080} open onClose={onClose}
      footer={
        <Space style={{ display: 'flex', justifyContent: 'space-between' }}>
          <Text>
            退货金额：<Text strong style={{ color: '#fa8c16', fontSize: 16 }}>{fmtAmount(total)}</Text>
          </Text>
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button type="primary" disabled={!preview} loading={createMut.isPending} onClick={submit}>
              生成退货单（草稿）
            </Button>
          </Space>
        </Space>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          placeholder="选择已审核的入库单"
          style={{ width: 360 }}
          showSearch
          optionFilterProp="label"
          loading={auditedInstocks.isLoading}
          options={instockOptions}
          onChange={(v) => void pickInstock(v)}
        />
        <DatePicker value={returnDate} onChange={(d) => setReturnDate(d ?? dayjs())} />
      </Space>

      {loadingPreview && <Card loading style={{ border: 'none' }} />}

      {!loadingPreview && preview && (
        <>
          <Descriptions bordered size="small" column={3} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="来源入库单">{preview.instockNo}</Descriptions.Item>
            <Descriptions.Item label="供应商">{preview.supplierName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="退货仓库">{preview.warehouseName ?? '-'}</Descriptions.Item>
          </Descriptions>
          {rows.length === 0 ? (
            <Text type="warning">该入库单所有明细已全部退完，无可退数量</Text>
          ) : (
            <Table<EditableReturnRow>
              rowKey="instockDetailId"
              size="small"
              columns={editColumns}
              dataSource={rows}
              pagination={false}
              scroll={{ x: 950 }}
            />
          )}
          <Input.TextArea
            rows={2}
            style={{ marginTop: 12 }}
            maxLength={500}
            placeholder="退货原因/备注"
            value={remark}
            onChange={(e) => setRemark(e.target.value)}
          />
        </>
      )}

      {!loadingPreview && !preview && (
        <Card style={{ border: 'none' }}>
          <Text type="secondary">请先选择来源入库单（仅已审核状态可选）</Text>
        </Card>
      )}
    </Drawer>
  )
}
