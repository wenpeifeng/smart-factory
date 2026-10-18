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
import { PrinterOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { usePrint } from '@/hooks/usePrint'
import {
  BIZ_STATUS,
  auditOutstock,
  createOutstock,
  deleteOutstock,
  getOrder,
  getOutstock,
  pageOrders,
  pageOutstock,
  type SalOrderDetailRow,
  type SalOutstockDetailRow,
  type SalOutstockFormPayload,
  type SalOutstockRow,
} from '@/api/sales'
import { reverseDoc } from '@/api/basedata'
import { formatDateTime } from '@/utils/date'

const { Title } = Typography

/** 出库创建表单值 */
interface OutstockFormValues {
  orderId: number
  outDate: dayjs.Dayjs
  remark?: string
}

/** 各订单行的本次出库数量（orderDetailId → 数量） */
type LineQtyMap = Record<number, number>

/**
 * 销售出库页：列表 + Drawer 详情 + 选单生成 Modal。
 * 审核执行：扣库存 → 写流水 → 生成应收 → 回写订单（库存不足整体报错回滚）。
 */
export default function OutstockList() {
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<number | undefined>(undefined)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [detailId, setDetailId] = useState<number | null>(null)
  const [createOpen, setCreateOpen] = useState(false)

  const queryClient = useQueryClient()

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['sales', 'outstock', keyword, status, pageNum, pageSize],
    queryFn: () =>
      pageOutstock({
        pageNum,
        pageSize,
        keyword: keyword || undefined,
        status,
      }),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['sales', 'outstock'] })
  const { openPrint } = usePrint()

  const handleAudit = async (id: number) => {
    try {
      await auditOutstock(id)
      message.success('审核成功：已扣减库存并生成应收')
      invalidate()
    } catch {
      /* 错误提示由请求拦截器统一处理（如库存不足） */
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await deleteOutstock(id)
      message.success('删除成功')
      invalidate()
    } catch {
      /* 同上 */
    }
  }

  /** 红冲已审核出库单：生成负数反向出库单并反向过账（不可撤销） */
  const handleReverse = (row: SalOutstockRow) => {
    let reason = ''
    Modal.confirm({
      title: `确认红冲出库单 ${row.outNo}？`,
      content: (
        <div>
          <p style={{ color: '#cf1322' }}>将生成负数反向出库单并反向过账，操作不可撤销。</p>
          <Input placeholder="红冲原因（选填）" maxLength={200} onChange={(e) => (reason = e.target.value)} />
        </div>
      ),
      okText: '确认红冲',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          const res = await reverseDoc('SAL_OUTSTOCK', row.id, reason)
          message.success(`红冲成功：已生成反向单 ${res.reverseDocNo}`)
          invalidate()
        } catch {
          /* 错误提示由请求拦截器统一处理 */
        }
      },
    })
  }

  const columns: ColumnsType<SalOutstockRow> = [
    { title: '出库单号', dataIndex: 'outNo', width: 150 },
    { title: '来源订单号', dataIndex: 'orderNo', width: 150 },
    { title: '客户', dataIndex: 'customerName', width: 150, ellipsis: true },
    { title: '仓库', dataIndex: 'warehouseName', width: 110, ellipsis: true },
    { title: '出库日期', dataIndex: 'outDate', width: 110 },
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
    { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatDateTime },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, row) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => setDetailId(row.id)}>
            详情
          </Button>
          {row.status === 0 && (
            <HasPerm code="sales:outstock:audit">
              <Popconfirm title="审核将扣减库存并生成应收，确认？" onConfirm={() => handleAudit(row.id)}>
                <Button type="link" size="small">
                  审核
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="sales:outstock:delete">
            <Popconfirm title="确认删除该草稿出库单？" onConfirm={() => handleDelete(row.id)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
            </HasPerm>
          )}
          {row.status === 10 && (
            <HasPerm code="sales:outstock:reverse">
              <Button type="link" size="small" danger onClick={() => handleReverse(row)}>
                红冲
              </Button>
            </HasPerm>
          )}
          {row.status !== 0 && (
            <HasPerm code="sales:outstock:list">
              <Button
                type="link"
                size="small"
                icon={<PrinterOutlined />}
                onClick={() => openPrint('delivery_note', row.id)}
              >
                打印
              </Button>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        销售出库
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Input.Search
            placeholder="出库单号 / 订单号 / 客户"
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
          <HasPerm code="sales:outstock:add">
            <Button type="primary" onClick={() => setCreateOpen(true)}>
              从订单生成出库
            </Button>
          </HasPerm>
        </Space>

        <Table<SalOutstockRow>
          className="erp-nowrap-table"
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          tableLayout="fixed"
          scroll={{ x: 1240 }}
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

      {/* 详情 Drawer */}
      <OutstockDetailDrawer id={detailId} onClose={() => setDetailId(null)} />

      {/* 选单生成 Modal */}
      <OutstockCreateModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onSuccess={invalidate}
      />
    </div>
  )
}

/** 出库单详情 Drawer：表头 + 明细（含已退/可退数量） */
function OutstockDetailDrawer({ id, onClose }: { id: number | null; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['sales', 'outstock', 'detail', id],
    queryFn: () => getOutstock(id as number),
    enabled: id != null,
  })

  const lineColumns: ColumnsType<SalOutstockDetailRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 120 },
    { title: '物料名称', dataIndex: 'materialName', ellipsis: true },
    { title: '规格', dataIndex: 'materialSpec', width: 130, ellipsis: true },
    { title: '数量', dataIndex: 'qty', width: 90, align: 'right' },
    { title: '单价', dataIndex: 'price', width: 90, align: 'right' },
    { title: '金额', dataIndex: 'amount', width: 100, align: 'right' },
    { title: '已退', dataIndex: 'returnedQty', width: 80, align: 'right' },
    { title: '可退', dataIndex: 'returnableQty', width: 80, align: 'right' },
  ]

  const out = data?.outstock
  const statusMeta = out ? BIZ_STATUS[out.status] : undefined

  return (
    <Drawer open={id != null} onClose={onClose} width={760} title="销售出库单详情">
      {isLoading || !out ? (
        <div style={{ padding: 24, textAlign: 'center', color: '#999' }}>加载中…</div>
      ) : (
        <>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="出库单号">{out.outNo}</Descriptions.Item>
            <Descriptions.Item label="状态">
              {statusMeta ? <Tag color={statusMeta.color}>{statusMeta.label}</Tag> : out.status}
            </Descriptions.Item>
            <Descriptions.Item label="来源订单">{out.orderNo}</Descriptions.Item>
            <Descriptions.Item label="客户">{out.customerName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="仓库">{out.warehouseName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="出库日期">{out.outDate}</Descriptions.Item>
            <Descriptions.Item label="金额">{Number(out.totalAmount).toFixed(2)}</Descriptions.Item>
            <Descriptions.Item label="数量">{Number(out.totalQty).toFixed(2)}</Descriptions.Item>
            <Descriptions.Item label="审核时间">{formatDateTime(out.auditTime)}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{formatDateTime(out.createTime)}</Descriptions.Item>
            <Descriptions.Item label="备注" span={2}>
              {out.remark || '-'}
            </Descriptions.Item>
          </Descriptions>

          <Title level={5} style={{ margin: '16px 0 8px' }}>
            明细行
          </Title>
          <Table<SalOutstockDetailRow>
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

/** 从销售订单选单生成出库单 Modal：选订单 → 按行填本次出库数量 */
function OutstockCreateModal({
  open,
  onClose,
  onSuccess,
}: {
  open: boolean
  onClose: () => void
  onSuccess: () => void
}) {
  const [form] = Form.useForm<OutstockFormValues>()
  const [orderId, setOrderId] = useState<number | null>(null)
  const [lineQty, setLineQty] = useState<LineQtyMap>({})

  // 可出库订单（已审核/部分出库），仅创建弹窗打开时拉取
  const { data: orderPage } = useQuery({
    queryKey: ['sales', 'outstock', 'create', 'orders'],
    queryFn: () => pageOrders({ pageNum: 1, pageSize: 200 }),
    enabled: open,
  })
  const availableOrders = (orderPage?.records ?? []).filter((o) => o.status === 10 || o.status === 20)

  // 选中订单后加载明细（含剩余可出数量）
  const { data: orderDetail, isLoading: detailLoading } = useQuery({
    queryKey: ['sales', 'orders', 'detail', orderId],
    queryFn: () => getOrder(orderId as number),
    enabled: open && orderId != null,
  })

  const handleAfterOpenChange = (visible: boolean) => {
    if (!visible) {
      setOrderId(null)
      setLineQty({})
      form.resetFields()
    } else {
      form.setFieldsValue({ outDate: dayjs() })
    }
  }

  const handleOrderChange = (id: number) => {
    setOrderId(id)
    setLineQty({})
  }

  const handleSubmit = async (values: OutstockFormValues) => {
    if (orderId == null) {
      message.warning('请先选择销售订单')
      return
    }
    const details = Object.entries(lineQty)
      .map(([orderDetailId, qty]) => ({ orderDetailId: Number(orderDetailId), qty }))
      .filter((d) => d.qty > 0)
    if (details.length === 0) {
      message.warning('请至少填写一行出库数量')
      return
    }
    const payload: SalOutstockFormPayload = {
      orderId,
      outDate: values.outDate.format('YYYY-MM-DD'),
      remark: values.remark,
      details,
    }
    try {
      await createOutstock(payload)
      message.success('出库单已生成（草稿），审核后扣减库存')
      onSuccess()
      onClose()
    } catch {
      /* 错误提示由请求拦截器统一处理 */
    }
  }

  const lines = orderDetail?.details ?? []
  const lineColumns: ColumnsType<SalOrderDetailRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 110 },
    { title: '物料名称', dataIndex: 'materialName', ellipsis: true },
    { title: '订购', dataIndex: 'qty', width: 80, align: 'right' },
    { title: '已出库', dataIndex: 'outQty', width: 80, align: 'right' },
    { title: '剩余可出', dataIndex: 'remainingQty', width: 90, align: 'right' },
    {
      title: '本次出库',
      width: 130,
      render: (_, row) => {
        const remaining = Number(row.remainingQty ?? 0)
        if (remaining <= 0) return <Tag>已出完</Tag>
        return (
          <InputNumber
            size="small"
            min={0}
            max={remaining}
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
      title="从销售订单生成出库单"
      width={880}
      onCancel={onClose}
      onOk={() => form.submit()}
      afterOpenChange={handleAfterOpenChange}
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit}>
        <Space size="middle" style={{ display: 'flex' }} wrap>
          <Form.Item
            name="orderId"
            label="销售订单（已审核/部分出库）"
            rules={[{ required: true, message: '请选择订单' }]}
            style={{ minWidth: 320 }}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择销售订单"
              value={orderId ?? undefined}
              onChange={handleOrderChange}
              options={availableOrders.map((o) => ({
                value: o.id,
                label: `${o.orderNo}｜${o.customerName ?? '-'}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="outDate"
            label="出库日期"
            rules={[{ required: true, message: '请选择日期' }]}
          >
            <DatePicker style={{ width: 160 }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={1} maxLength={500} placeholder="选填" />
        </Form.Item>
      </Form>

      {orderId != null &&
        (detailLoading ? (
          <div style={{ padding: 24, textAlign: 'center', color: '#999' }}>订单明细加载中…</div>
        ) : (
          <Table<SalOrderDetailRow>
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
