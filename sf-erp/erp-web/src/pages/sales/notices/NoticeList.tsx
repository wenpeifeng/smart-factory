import HasPerm from '@/components/HasPerm'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import StatusTag from '@/components/StatusTag'
import { useDebounce } from '@/hooks/useDebounce'
import { useSubmitting } from '@/hooks/useSubmitting'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Table,
  Tag,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { useState } from 'react'
import {
  NOTICE_STATUS,
  cancelNotice,
  createNotice,
  deleteNotice,
  getCustomerOptions,
  getMaterialOptions,
  getNotice,
  getPendingStocks,
  getOrder,
  getWarehouseOptions,
  notifyNotice,
  pageOrders,
  settleNotice,
  updateNotice,
  type PendingStockRow,
  type SalNoticeFormPayload,
  type SalNoticeItemRow,
  type SalNoticeRow,
  type SalOrderDetailRow,
} from '@/api/sales'

/** 通知单表单值 */
interface NoticeFormValues {
  mode: 'order' | 'direct'
  orderId?: number
  customerId?: number
  warehouseId?: number
  planDate: Dayjs
  remark?: string
}

/** 直发明细行表单值 */
interface DirectLineValues {
  materialId?: number
  qty: number
  remark?: string
}

/** 各订单行的本次通知数量（orderDetailId → 数量） */
type LineQtyMap = Record<number, number>

/**
 * 销售发货通知单页：列表 + 详情 Drawer + 从订单生成/直发新建 Modal
 * + 备货参考 Drawer（物料×仓库可用量）+ 按出库单号核销。
 * 状态机：草稿可改/可删/可通知；已通知可核销（守卫不超通知量）/可取消（未出库）；
 * 全部核销到位自动流转已出库。出库挂点：出库单 remark 记 FH 单号 + 本页核销。
 */
export default function NoticeList() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebounce(keyword)
  const [status, setStatus] = useState<number | undefined>()

  const [detailId, setDetailId] = useState<number | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [stockDrawerOpen, setStockDrawerOpen] = useState(false)
  const [settleNoticeId, setSettleNoticeId] = useState<number | null>(null)

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['sales', 'notices'] })

  const notifySubmit = useSubmitting((id: number) => notifyNotice(id), {
    successText: '已通知发货',
    onSuccess: invalidate,
  })
  const cancelSubmit = useSubmitting((id: number) => cancelNotice(id), {
    successText: '已取消',
    onSuccess: invalidate,
  })
  const deleteSubmit = useSubmitting((id: number) => deleteNotice(id), {
    successText: '已删除',
    onSuccess: invalidate,
  })

  const columns: ColumnsType<SalNoticeRow> = [
    { title: '通知单号', dataIndex: 'noticeNo', width: 150 },
    {
      title: '来源订单',
      dataIndex: 'orderNo',
      width: 150,
      render: (v: string | null) => (v ? v : <Tag>直发</Tag>),
    },
    { title: '客户', dataIndex: 'customerName', width: 150, ellipsis: true },
    { title: '仓库', dataIndex: 'warehouseName', width: 110, ellipsis: true },
    { title: '计划发货', dataIndex: 'planDate', width: 105 },
    {
      title: '通知数量',
      dataIndex: 'totalQty',
      width: 95,
      align: 'right',
      render: (v: number | null) => (v == null ? '-' : Number(v).toFixed(2)),
    },
    {
      title: '已出库',
      dataIndex: 'totalOutQty',
      width: 95,
      align: 'right',
      render: (v: number | null) => (v == null ? '-' : Number(v).toFixed(2)),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) => {
        const s = NOTICE_STATUS[v]
        return s ? <StatusTag value={s.label} semantic={s.semantic} /> : <StatusTag value={v} />
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 160 },
    {
      title: '操作',
      key: 'action',
      width: 250,
      render: (_, row) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => setDetailId(row.id)}>
            详情
          </Button>
          {row.status === 0 && (
            <HasPerm code="sales:deliveryNotice:edit">
              <Button type="link" size="small" onClick={() => openEdit(row)}>
                编辑
              </Button>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="sales:deliveryNotice:audit">
              <Popconfirm title="通知后将开始备货发货，确认通知？" onConfirm={() => notifySubmit.run(row.id)}>
                <Button type="link" size="small" loading={notifySubmit.loading}>
                  通知
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 10 && (
            <HasPerm code="sales:deliveryNotice:settle">
              <Button type="link" size="small" onClick={() => setSettleNoticeId(row.id)}>
                核销
              </Button>
            </HasPerm>
          )}
          {(row.status === 0 || row.status === 10) && (
            <HasPerm code="sales:deliveryNotice:cancel">
              <Popconfirm title="取消后通知单终止，仅未发生出库可取消，确认？" onConfirm={() => cancelSubmit.run(row.id)}>
                <Button type="link" size="small" danger loading={cancelSubmit.loading}>
                  取消
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="sales:deliveryNotice:delete">
              <Popconfirm title="确认删除该草稿通知单？" onConfirm={() => deleteSubmit.run(row.id)}>
                <Button type="link" size="small" danger loading={deleteSubmit.loading}>
                  删除
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  const openEdit = (row: SalNoticeRow) => {
    setEditingId(row.id)
    setEditOpen(true)
  }

  const toolbar = (
    <>
      <Input.Search
        placeholder="通知单号 / 订单号 / 客户"
        style={{ width: 240 }}
        allowClear
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
      />
      <Select
        style={{ width: 120 }}
        allowClear
        placeholder="状态筛选"
        options={Object.entries(NOTICE_STATUS).map(([v, s]) => ({ value: Number(v), label: s.label }))}
        value={status}
        onChange={(v) => setStatus(v)}
      />
      <Button onClick={() => setStockDrawerOpen(true)}>备货参考</Button>
      <HasPerm code="sales:deliveryNotice:add">
        <Button
          type="primary"
          onClick={() => {
            setEditingId(null)
            setEditOpen(true)
          }}
        >
          新建通知单
        </Button>
      </HasPerm>
    </>
  )

  return (
    <div className="erp-page">
      <PageHeader
        title="发货通知单"
        subtitle="从已审核订单按余量生成（或直发）；出库后在出库单备注记 FH 单号并回此核销出库数量"
      />
      <PageTable<SalNoticeRow>
        url="/sales/delivery-notices"
        queryKey={['sales', 'notices', debouncedKeyword, status]}
        params={{ keyword: debouncedKeyword || undefined, status }}
        columns={columns}
        toolbar={toolbar}
      />

      {/* 详情 Drawer */}
      <NoticeDetailDrawer id={detailId} onClose={() => setDetailId(null)} />

      {/* 从订单生成 / 直发新建 / 编辑 Modal */}
      <NoticeFormModal
        open={editOpen}
        editingId={editingId}
        onClose={() => setEditOpen(false)}
        onSuccess={invalidate}
      />

      {/* 备货参考 Drawer */}
      <PendingStockDrawer open={stockDrawerOpen} onClose={() => setStockDrawerOpen(false)} />

      {/* 核销 Modal */}
      <SettleModal noticeId={settleNoticeId} onClose={() => setSettleNoticeId(null)} onSuccess={invalidate} />
    </div>
  )
}

/** 通知单详情 Drawer：表头 + 明细（通知/已出库/剩余数量） */
function NoticeDetailDrawer({ id, onClose }: { id: number | null; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['sales', 'notices', 'detail', id],
    queryFn: () => getNotice(id as number),
    enabled: id != null,
  })

  const lineColumns: ColumnsType<SalNoticeItemRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 110 },
    { title: '物料名称', dataIndex: 'materialName', ellipsis: true },
    { title: '规格', dataIndex: 'materialSpec', width: 120, ellipsis: true },
    { title: '通知数量', dataIndex: 'qty', width: 90, align: 'right' },
    { title: '已出库', dataIndex: 'outQty', width: 90, align: 'right' },
    {
      title: '剩余待出',
      dataIndex: 'remainingQty',
      width: 90,
      align: 'right',
      render: (v: number | null) => (v == null ? '-' : Number(v).toFixed(2)),
    },
    {
      title: '来源订单行',
      dataIndex: 'orderDetailId',
      width: 100,
      render: (v: number | null) => v ?? <Tag>直发</Tag>,
    },
  ]

  const notice = data?.notice
  const statusMeta = notice ? NOTICE_STATUS[notice.status] : undefined

  return (
    <Drawer open={id != null} onClose={onClose} width={820} title="发货通知单详情">
      {isLoading || !notice ? (
        <div style={{ padding: 24, textAlign: 'center', color: '#999' }}>加载中…</div>
      ) : (
        <>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="通知单号">{notice.noticeNo}</Descriptions.Item>
            <Descriptions.Item label="状态">
              {statusMeta ? <StatusTag value={statusMeta.label} semantic={statusMeta.semantic} /> : notice.status}
            </Descriptions.Item>
            <Descriptions.Item label="来源订单">{notice.orderNo ?? '直发（无来源）'}</Descriptions.Item>
            <Descriptions.Item label="客户">
              {notice.customerCode ? `${notice.customerCode} ` : ''}
              {notice.customerName ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="仓库">{notice.warehouseName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="计划发货日期">{notice.planDate}</Descriptions.Item>
            <Descriptions.Item label="通知时间">{notice.notifyTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="最近核销时间">{notice.settleTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="取消时间">{notice.cancelTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{notice.createTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="备注" span={2}>
              {notice.remark || '-'}
            </Descriptions.Item>
          </Descriptions>

          <div style={{ margin: '16px 0 8px', fontWeight: 600 }}>明细行</div>
          {data?.items?.length ? (
            <Table<SalNoticeItemRow>
              rowKey="id"
              size="small"
              columns={lineColumns}
              dataSource={data.items}
              pagination={false}
            />
          ) : (
            <Empty description="暂无明细行" />
          )}
        </>
      )}
    </Drawer>
  )
}

/**
 * 新建/编辑通知单 Modal。
 * 两种模式：从订单生成（选已审核订单，按行余量填通知数量）/ 直发
 * （手工选客户、仓库与物料）。编辑仅草稿可进，模式跟随原单来源。
 */
function NoticeFormModal({
  open,
  editingId,
  onClose,
  onSuccess,
}: {
  open: boolean
  editingId: number | null
  onClose: () => void
  onSuccess: () => void
}) {
  const [form] = Form.useForm<NoticeFormValues>()
  const [mode, setMode] = useState<'order' | 'direct'>('order')
  const [orderId, setOrderId] = useState<number | null>(null)
  const [lineQty, setLineQty] = useState<LineQtyMap>({})
  const [directLines, setDirectLines] = useState<DirectLineValues[]>([{ qty: 1 }])

  const { data: customers = [] } = useQuery({
    queryKey: ['sales', 'options', 'customers'],
    queryFn: getCustomerOptions,
    enabled: open && mode === 'direct',
  })
  const { data: warehouses = [] } = useQuery({
    queryKey: ['sales', 'options', 'warehouses'],
    queryFn: getWarehouseOptions,
    enabled: open && mode === 'direct',
  })
  const { data: materials = [] } = useQuery({
    queryKey: ['sales', 'options', 'materials'],
    queryFn: getMaterialOptions,
    enabled: open && mode === 'direct',
  })
  // 可选订单（已审核/部分出库）
  const { data: orderPage } = useQuery({
    queryKey: ['sales', 'notices', 'create', 'orders'],
    queryFn: () => pageOrders({ pageNum: 1, pageSize: 200 }),
    enabled: open && mode === 'order',
  })
  const availableOrders = (orderPage?.records ?? []).filter((o) => o.status === 10 || o.status === 20)

  // 编辑回填：原单是否带来源决定模式；行数据展开回填
  const { data: origin } = useQuery({
    queryKey: ['sales', 'notices', 'detail', editingId],
    queryFn: () => getNotice(editingId as number),
    enabled: open && editingId != null,
  })

  const afterOpenChange = (visible: boolean) => {
    if (!visible) {
      setOrderId(null)
      setLineQty({})
      setDirectLines([{ qty: 1 }])
      return
    }
    form.resetFields()
    form.setFieldsValue({ planDate: dayjs(), mode: 'order' })
    if (editingId != null && origin) {
      const fromOrder = origin.notice.orderId != null
      setMode(fromOrder ? 'order' : 'direct')
      form.setFieldsValue({
        mode: fromOrder ? 'order' : 'direct',
        orderId: origin.notice.orderId ?? undefined,
        customerId: origin.notice.customerId,
        warehouseId: origin.notice.warehouseId,
        remark: origin.notice.remark ?? undefined,
        planDate: dayjs(origin.notice.planDate),
      })
      if (fromOrder) {
        setOrderId(origin.notice.orderId)
        setLineQty(Object.fromEntries(origin.items.map((i) => [i.orderDetailId as number, Number(i.qty)])))
      } else {
        setDirectLines(
          origin.items.map((i) => ({ materialId: i.materialId, qty: Number(i.qty), remark: i.remark ?? undefined })),
        )
      }
    } else {
      setMode('order')
    }
  }

  const handleModeChange = (m: 'order' | 'direct') => {
    setMode(m)
    setOrderId(null)
    setLineQty({})
    setDirectLines([{ qty: 1 }])
  }

  const { data: orderDetail, isLoading: detailLoading } = useQuery({
    queryKey: ['sales', 'orders', 'detail', orderId],
    queryFn: () => getOrder(orderId as number),
    enabled: open && mode === 'order' && orderId != null,
  })

  const submit = useSubmitting(
    async (values: NoticeFormValues) => {
      const payload: SalNoticeFormPayload = {
        planDate: values.planDate.format('YYYY-MM-DD'),
        remark: values.remark,
        details: [],
      }
      if (mode === 'order') {
        if (orderId == null) throw new Error('请先选择销售订单')
        payload.orderId = orderId
        payload.details = Object.entries(lineQty)
          .map(([orderDetailId, qty]) => ({ orderDetailId: Number(orderDetailId), qty }))
          .filter((d) => d.qty > 0)
      } else {
        payload.details = directLines
          .filter((d) => d.materialId != null && d.qty > 0)
          .map((d) => ({ materialId: d.materialId, qty: d.qty, remark: d.remark }))
      }
      if (payload.details.length === 0) throw new Error('请至少填写一行通知数量')
      if (editingId != null) {
        await updateNotice(editingId, payload)
      } else {
        await createNotice(payload)
      }
    },
    {
      successText: editingId != null ? '修改成功' : '通知单已创建（草稿）',
      onSuccess: () => {
        onSuccess()
        onClose()
      },
    },
  )

  const orderLineColumns: ColumnsType<SalOrderDetailRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 110 },
    { title: '物料名称', dataIndex: 'materialName', ellipsis: true },
    { title: '订购', dataIndex: 'qty', width: 80, align: 'right' },
    { title: '已出库', dataIndex: 'outQty', width: 80, align: 'right' },
    { title: '剩余可出', dataIndex: 'remainingQty', width: 90, align: 'right' },
    {
      title: '本次通知',
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
      title={editingId != null ? '编辑发货通知单（草稿）' : '新建发货通知单'}
      width={880}
      onCancel={onClose}
      onOk={() => form.submit()}
      okButtonProps={{ loading: submit.loading }}
      destroyOnClose
      afterOpenChange={afterOpenChange}
    >
      <Form form={form} layout="vertical" onFinish={(v) => submit.run(v)}>
        <Form.Item name="mode" label="来源方式" initialValue="order">
          <Radio.Group
            value={mode}
            onChange={(e) => handleModeChange(e.target.value as 'order' | 'direct')}
            disabled={editingId != null}
            options={[
              { label: '从销售订单生成（按余量）', value: 'order' },
              { label: '无来源直发', value: 'direct' },
            ]}
          />
        </Form.Item>

        <Space size="middle" style={{ display: 'flex' }} wrap>
          {mode === 'order' ? (
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
                onChange={(id) => {
                  setOrderId(id)
                  setLineQty({})
                }}
                options={availableOrders.map((o) => ({
                  value: o.id,
                  label: `${o.orderNo}｜${o.customerName ?? '-'}`,
                }))}
              />
            </Form.Item>
          ) : (
            <>
              <Form.Item
                name="customerId"
                label="客户"
                rules={[{ required: true, message: '请选择客户' }]}
                style={{ minWidth: 240 }}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择客户"
                  options={customers.map((c) => ({ value: c.id, label: `${c.code} ${c.name}` }))}
                />
              </Form.Item>
              <Form.Item
                name="warehouseId"
                label="发货仓库"
                rules={[{ required: true, message: '请选择仓库' }]}
                style={{ minWidth: 180 }}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择仓库"
                  options={warehouses.map((w) => ({ value: w.id, label: w.name }))}
                />
              </Form.Item>
            </>
          )}
          <Form.Item
            name="planDate"
            label="计划发货日期"
            rules={[{ required: true, message: '请选择计划发货日期' }]}
          >
            <DatePicker style={{ width: 150 }} />
          </Form.Item>
        </Space>

        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={1} maxLength={500} placeholder="选填" />
        </Form.Item>
      </Form>

      {mode === 'order' ? (
        orderId != null ? (
          detailLoading ? (
            <div style={{ padding: 24, textAlign: 'center', color: '#999' }}>订单明细加载中…</div>
          ) : (
            <Table<SalOrderDetailRow>
              rowKey="id"
              size="small"
              columns={orderLineColumns}
              dataSource={orderDetail?.details ?? []}
              pagination={false}
            />
          )
        ) : (
          <Empty description="请先选择销售订单" />
        )
      ) : (
        <div>
          <div style={{ margin: '8px 0', fontWeight: 600 }}>直发明细行</div>
          {directLines.map((line, idx) => (
            <Space key={idx} align="baseline" style={{ display: 'flex', marginBottom: 4 }}>
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="物料"
                style={{ minWidth: 300 }}
                value={line.materialId}
                options={materials.map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))}
                onChange={(v) =>
                  setDirectLines((prev) => prev.map((l, i) => (i === idx ? { ...l, materialId: v } : l)))
                }
              />
              <InputNumber
                min={0.0001}
                precision={4}
                placeholder="通知数量"
                style={{ width: 120 }}
                value={line.qty}
                onChange={(v) => setDirectLines((prev) => prev.map((l, i) => (i === idx ? { ...l, qty: v ?? 0 } : l)))}
              />
              <Button
                type="link"
                danger
                size="small"
                disabled={directLines.length <= 1}
                onClick={() => setDirectLines((prev) => prev.filter((_, i) => i !== idx))}
              >
                删除
              </Button>
            </Space>
          ))}
          <Button type="dashed" block onClick={() => setDirectLines((prev) => [...prev, { qty: 1 }])}>
            + 添加明细行
          </Button>
        </div>
      )}
    </Modal>
  )
}

/** 备货参考 Drawer：物料 × 仓库可用量（发货前查仓） */
function PendingStockDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [warehouseId, setWarehouseId] = useState<number | undefined>()
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebounce(keyword)

  const { data: warehouses = [] } = useQuery({
    queryKey: ['sales', 'options', 'warehouses'],
    queryFn: getWarehouseOptions,
    enabled: open,
  })
  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['sales', 'notices', 'pending-stocks', warehouseId, debouncedKeyword],
    queryFn: () => getPendingStocks(warehouseId, debouncedKeyword || undefined),
    enabled: open,
  })

  const columns: ColumnsType<PendingStockRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 120 },
    { title: '物料名称', dataIndex: 'materialName', ellipsis: true },
    { title: '规格', dataIndex: 'materialSpec', width: 120, ellipsis: true },
    { title: '仓库', dataIndex: 'warehouseName', width: 140, ellipsis: true },
    {
      title: '可用量',
      dataIndex: 'availableQty',
      width: 110,
      align: 'right',
      render: (v: number) => Number(v ?? 0).toFixed(2),
    },
  ]

  return (
    <Drawer open={open} onClose={onClose} width={720} title="备货参考（物料 × 仓库可用量）">
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          style={{ width: 180 }}
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="仓库筛选"
          options={warehouses.map((w) => ({ value: w.id, label: w.name }))}
          value={warehouseId}
          onChange={setWarehouseId}
        />
        <Input.Search
          placeholder="物料编码 / 名称"
          style={{ width: 220 }}
          allowClear
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
      </Space>
      <Table<PendingStockRow>
        rowKey={(r) => `${r.materialId}-${r.warehouseId}`}
        size="small"
        columns={columns}
        dataSource={rows}
        loading={isLoading}
        pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
      />
    </Drawer>
  )
}

/** 核销 Modal：录入已审核出库单号，按物料核销到通知行（守卫不超通知量） */
function SettleModal({
  noticeId,
  onClose,
  onSuccess,
}: {
  noticeId: number | null
  onClose: () => void
  onSuccess: () => void
}) {
  const [form] = Form.useForm<{ outstockNo: string }>()

  const { data: detail } = useQuery({
    queryKey: ['sales', 'notices', 'detail', noticeId],
    queryFn: () => getNotice(noticeId as number),
    enabled: noticeId != null,
  })

  const submit = useSubmitting(
    async (values: { outstockNo: string }) => {
      if (noticeId == null) return
      await settleNotice(noticeId, values.outstockNo.trim())
    },
    {
      successText: '核销成功',
      onSuccess: () => {
        onSuccess()
        onClose()
      },
    },
  )

  const remainingLines = (detail?.items ?? []).filter((i) => Number(i.remainingQty ?? 0) > 0)

  return (
    <Modal
      open={noticeId != null}
      title="按出库单号核销出库数量"
      width={560}
      onCancel={onClose}
      onOk={() => form.submit()}
      okButtonProps={{ loading: submit.loading }}
      okText="核销"
      destroyOnClose
      afterOpenChange={(visible) => visible && form.resetFields()}
    >
      <Form form={form} layout="vertical" onFinish={(v) => submit.run(v)}>
        <Form.Item
          name="outstockNo"
          label="销售出库单号（XS 前缀，须已审核）"
          extra="出库挂点约定：出库单创建时在备注记录本通知单号（FH 前缀），出库审核后在此核销；超通知量会被拒绝"
          rules={[{ required: true, message: '请输入出库单号' }]}
        >
          <Input placeholder="如 XS202609050001" maxLength={40} allowClear />
        </Form.Item>
      </Form>
      {detail && (
        <div>
          <div style={{ margin: '8px 0', fontWeight: 600 }}>当前待出库明细</div>
          {remainingLines.length ? (
            <Table<SalNoticeItemRow>
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={remainingLines}
              columns={[
                { title: '物料', dataIndex: 'materialName', ellipsis: true },
                { title: '通知数量', dataIndex: 'qty', width: 100, align: 'right' },
                { title: '已出库', dataIndex: 'outQty', width: 90, align: 'right' },
                { title: '剩余', dataIndex: 'remainingQty', width: 90, align: 'right' },
              ]}
            />
          ) : (
            <Empty description="全部明细已核销完毕" />
          )}
        </div>
      )}
    </Modal>
  )
}
