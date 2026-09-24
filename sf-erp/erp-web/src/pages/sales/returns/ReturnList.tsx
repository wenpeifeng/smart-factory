import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  DatePicker,
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
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  BIZ_STATUS,
  auditReturn,
  createReturn,
  deleteReturn,
  getOutstock,
  getReturn,
  pageOutstock,
  pageReturns,
  type SalOutstockDetailRow,
  type SalReturnDetailRow,
  type SalReturnFormPayload,
  type SalReturnRow,
} from '@/api/sales'
import { reverseDoc } from '@/api/basedata'

const { Title } = Typography

/** 退货创建表单值 */
interface ReturnFormValues {
  outId: number
  returnDate: dayjs.Dayjs
  remark?: string
}

/** 各出库行的本次退货数量（outDetailId → 数量） */
type LineQtyMap = Record<number, number>

/**
 * 销售退货页：列表 + Drawer 详情 + 引用出库单退货 Modal。
 * 审核执行：回补库存 → 写正数流水 → 生成红字（负金额）应收 → 回写出库行已退数量。
 */
export default function ReturnList() {
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<number | undefined>(undefined)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [detailId, setDetailId] = useState<number | null>(null)
  const [createOpen, setCreateOpen] = useState(false)

  const queryClient = useQueryClient()

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['sales', 'returns', keyword, status, pageNum, pageSize],
    queryFn: () =>
      pageReturns({
        pageNum,
        pageSize,
        keyword: keyword || undefined,
        status,
      }),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['sales', 'returns'] })

  const handleAudit = async (id: number) => {
    try {
      await auditReturn(id)
      message.success('审核成功：已回补库存并生成红字应收')
      invalidate()
    } catch {
      /* 错误提示由请求拦截器统一处理 */
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await deleteReturn(id)
      message.success('删除成功')
      invalidate()
    } catch {
      /* 同上 */
    }
  }

  /** 红冲已审核退货单：生成负数反向退货单并反向过账（不可撤销） */
  const handleReverse = (row: SalReturnRow) => {
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
          const res = await reverseDoc('SAL_RETURN', row.id, reason)
          message.success(`红冲成功：已生成反向单 ${res.reverseDocNo}`)
          invalidate()
        } catch {
          /* 错误提示由请求拦截器统一处理 */
        }
      },
    })
  }

  const columns: ColumnsType<SalReturnRow> = [
    { title: '退货单号', dataIndex: 'returnNo', width: 150 },
    { title: '来源出库单号', dataIndex: 'outNo', width: 150 },
    { title: '客户', dataIndex: 'customerName', width: 150, ellipsis: true },
    { title: '仓库', dataIndex: 'warehouseName', width: 110, ellipsis: true },
    { title: '退货日期', dataIndex: 'returnDate', width: 110 },
    {
      title: '金额',
      dataIndex: 'totalAmount',
      width: 110,
      align: 'right',
      render: (v: number) =>
        v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 }),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) => {
        if (v === 20) return <Tag color="red">已红冲</Tag>
        const s = BIZ_STATUS[v]
        return s ? <Tag color={s.color}>{s.label}</Tag> : <Tag>{v}</Tag>
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 160 },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, row) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => setDetailId(row.id)}>
            详情
          </Button>
          {row.status === 0 && (
            <HasPerm code="sales:return:audit">
              <Popconfirm title="审核将回补库存并生成红字应收，确认？" onConfirm={() => handleAudit(row.id)}>
                <Button type="link" size="small">
                  审核
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 10 && (
            <HasPerm code="sales:return:reverse">
              <Button type="link" size="small" danger onClick={() => handleReverse(row)}>
                红冲
              </Button>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="sales:return:delete">
            <Popconfirm title="确认删除该草稿退货单？" onConfirm={() => handleDelete(row.id)}>
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
        销售退货
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Input.Search
            placeholder="退货单号 / 出库单号 / 客户"
            style={{ width: 240 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setKeyword(v)
            }}
          />
          <Select
            style={{ width: 120 }}
            allowClear
            placeholder="状态筛选"
            options={Object.entries(BIZ_STATUS).map(([v, s]) => ({ value: Number(v), label: s.label }))}
            onChange={(v) => {
              setPageNum(1)
              setStatus(v)
            }}
          />
          <HasPerm code="sales:return:add">
            <Button type="primary" onClick={() => setCreateOpen(true)}>
              引用出库单退货
            </Button>
          </HasPerm>
        </Space>

        <Table<SalReturnRow>
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          pagination={{
            total: data?.total ?? 0,
            current: pageNum,
            pageSize,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPageNum(p)
              setPageSize(s)
            },
          }}
        />
      </Card>

      <ReturnDetailDrawer id={detailId} onClose={() => setDetailId(null)} />

      <ReturnCreateModal open={createOpen} onClose={() => setCreateOpen(false)} onSuccess={invalidate} />
    </div>
  )
}

/** 退货单详情 Drawer */
function ReturnDetailDrawer({ id, onClose }: { id: number | null; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['sales', 'returns', 'detail', id],
    queryFn: () => getReturn(id as number),
    enabled: id != null,
  })

  const lineColumns: ColumnsType<SalReturnDetailRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 120 },
    { title: '物料名称', dataIndex: 'materialName', ellipsis: true },
    { title: '规格', dataIndex: 'materialSpec', width: 140, ellipsis: true },
    { title: '退货数量', dataIndex: 'qty', width: 100, align: 'right' },
    { title: '单价', dataIndex: 'price', width: 90, align: 'right' },
    { title: '金额', dataIndex: 'amount', width: 110, align: 'right' },
  ]

  const ret = data?.ret
  const statusMeta = ret ? BIZ_STATUS[ret.status] : undefined

  return (
    <Drawer open={id != null} onClose={onClose} width={760} title="销售退货单详情">
      {isLoading || !ret ? (
        <div style={{ padding: 24, textAlign: 'center', color: '#999' }}>加载中…</div>
      ) : (
        <>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="退货单号">{ret.returnNo}</Descriptions.Item>
            <Descriptions.Item label="状态">
              {statusMeta ? <Tag color={statusMeta.color}>{statusMeta.label}</Tag> : ret.status}
            </Descriptions.Item>
            <Descriptions.Item label="来源出库单">{ret.outNo}</Descriptions.Item>
            <Descriptions.Item label="客户">{ret.customerName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="退回仓库">{ret.warehouseName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="退货日期">{ret.returnDate}</Descriptions.Item>
            <Descriptions.Item label="金额（红字冲减）">
              -{Number(ret.totalAmount).toFixed(2)}
            </Descriptions.Item>
            <Descriptions.Item label="数量">{Number(ret.totalQty).toFixed(2)}</Descriptions.Item>
            <Descriptions.Item label="审核时间">{ret.auditTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{ret.createTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="备注" span={2}>
              {ret.remark || '-'}
            </Descriptions.Item>
          </Descriptions>

          <Title level={5} style={{ margin: '16px 0 8px' }}>
            明细行
          </Title>
          <Table<SalReturnDetailRow>
            rowKey="id"
            size="small"
            columns={lineColumns}
            dataSource={data?.details ?? []}
            pagination={false}
          />
        </>
      )}
    </Drawer>
  )
}

/** 引用出库单退货 Modal：选出库单 → 按行填本次退货数量 */
function ReturnCreateModal({
  open,
  onClose,
  onSuccess,
}: {
  open: boolean
  onClose: () => void
  onSuccess: () => void
}) {
  const [form] = Form.useForm<ReturnFormValues>()
  const [outId, setOutId] = useState<number | null>(null)
  const [lineQty, setLineQty] = useState<LineQtyMap>({})

  // 可退货出库单（已审核）
  const { data: outPage } = useQuery({
    queryKey: ['sales', 'returns', 'create', 'outstocks'],
    queryFn: () => pageOutstock({ pageNum: 1, pageSize: 200 }),
    enabled: open,
  })
  const availableOuts = (outPage?.records ?? []).filter((o) => o.status === 10)

  const { data: outDetail, isLoading: detailLoading } = useQuery({
    queryKey: ['sales', 'outstock', 'detail', outId],
    queryFn: () => getOutstock(outId as number),
    enabled: open && outId != null,
  })

  const handleAfterOpenChange = (visible: boolean) => {
    if (!visible) {
      setOutId(null)
      setLineQty({})
      form.resetFields()
    } else {
      form.setFieldsValue({ returnDate: dayjs() })
    }
  }

  const handleSubmit = async (values: ReturnFormValues) => {
    if (outId == null) {
      message.warning('请先选择销售出库单')
      return
    }
    const details = Object.entries(lineQty)
      .map(([outDetailId, qty]) => ({ outDetailId: Number(outDetailId), qty }))
      .filter((d) => d.qty > 0)
    if (details.length === 0) {
      message.warning('请至少填写一行退货数量')
      return
    }
    const payload: SalReturnFormPayload = {
      outId,
      returnDate: values.returnDate.format('YYYY-MM-DD'),
      remark: values.remark,
      details,
    }
    try {
      await createReturn(payload)
      message.success('退货单已生成（草稿），审核后回补库存')
      onSuccess()
      onClose()
    } catch {
      /* 错误提示由请求拦截器统一处理 */
    }
  }

  const lines = outDetail?.details ?? []
  const lineColumns: ColumnsType<SalOutstockDetailRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 110 },
    { title: '物料名称', dataIndex: 'materialName', ellipsis: true },
    { title: '出库数量', dataIndex: 'qty', width: 90, align: 'right' },
    { title: '已退', dataIndex: 'returnedQty', width: 80, align: 'right' },
    { title: '可退', dataIndex: 'returnableQty', width: 80, align: 'right' },
    {
      title: '本次退货',
      width: 130,
      render: (_, row) => {
        const returnable = Number(row.returnableQty ?? 0)
        if (returnable <= 0) return <Tag>不可退</Tag>
        return (
          <InputNumber
            size="small"
            min={0}
            max={returnable}
            precision={4}
            value={lineQty[row.id] ?? 0}
            onChange={(v) => setLineQty((prev) => ({ ...prev, [row.id]: v ?? 0 }))}
          />
        )
      },
    },
  ]

  return (
    <Modal
      open={open}
      title="引用销售出库单退货"
      width={880}
      onCancel={onClose}
      onOk={() => form.submit()}
      afterOpenChange={handleAfterOpenChange}
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit}>
        <Space size="middle" style={{ display: 'flex' }} wrap>
          <Form.Item
            name="outId"
            label="销售出库单（已审核）"
            rules={[{ required: true, message: '请选择出库单' }]}
            style={{ minWidth: 320 }}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择销售出库单"
              value={outId ?? undefined}
              onChange={(v: number) => {
                setOutId(v)
                setLineQty({})
              }}
              options={availableOuts.map((o) => ({
                value: o.id,
                label: `${o.outNo}｜${o.customerName ?? '-'}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="returnDate"
            label="退货日期"
            rules={[{ required: true, message: '请选择日期' }]}
          >
            <DatePicker style={{ width: 160 }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={1} maxLength={500} placeholder="选填" />
        </Form.Item>
      </Form>

      {outId != null &&
        (detailLoading ? (
          <div style={{ padding: 24, textAlign: 'center', color: '#999' }}>出库明细加载中…</div>
        ) : (
          <Table<SalOutstockDetailRow>
            rowKey="id"
            size="small"
            columns={lineColumns}
            dataSource={lines}
            pagination={false}
          />
        ))}
    </Modal>
  )
}
