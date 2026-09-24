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
import dayjs, { type Dayjs } from 'dayjs'
import { usePrint } from '@/hooks/usePrint'
import {
  ORDER_STATUS,
  auditOrder,
  closeOrder,
  createOrder,
  deleteOrder,
  getCustomerOptions,
  getMaterialOptions,
  getOrder,
  getWarehouseOptions,
  pageOrders,
  updateOrder,
  type SalOrderDetailRow,
  type SalOrderFormPayload,
  type SalOrderRow,
} from '@/api/sales'
// V131 价格中心：定价引擎参照（只读展示，不影响提交逻辑）
import PriceRefPopover from '@/pages/basedata/prices/PriceRefPopover'

const { Title } = Typography

/** 订单表单值（DatePicker 用 Dayjs 承接，提交前格式化） */
interface OrderFormValues {
  customerId: number
  warehouseId: number
  orderDate: Dayjs
  remark?: string
  details: { materialId: number; qty: number; price: number; remark?: string }[]
}

/**
 * 销售订单页：列表 + Drawer 详情 + 新增/编辑 Modal。
 * 状态机：草稿可改/可删/可审核；已审核·部分出库可关闭；审核含信用额度校验。
 */
export default function OrderList() {
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<number | undefined>(undefined)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  // 详情 Drawer / 编辑 Modal 状态
  const [detailId, setDetailId] = useState<number | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)

  const queryClient = useQueryClient()
  const { openPrint } = usePrint()

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['sales', 'orders', keyword, status, pageNum, pageSize],
    queryFn: () =>
      pageOrders({
        pageNum,
        pageSize,
        keyword: keyword || undefined,
        status,
      }),
  })

  /** 操作后刷新列表（审核/关闭会联动出库数量与状态） */
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['sales', 'orders'] })

  const handleAudit = async (id: number) => {
    try {
      await auditOrder(id)
      message.success('审核成功')
      invalidate()
    } catch {
      /* 错误提示由请求拦截器统一处理 */
    }
  }

  const handleClose = async (id: number) => {
    try {
      await closeOrder(id)
      message.success('关闭成功')
      invalidate()
    } catch {
      /* 同上 */
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await deleteOrder(id)
      message.success('删除成功')
      invalidate()
    } catch {
      /* 同上 */
    }
  }

  const openCreate = () => {
    setEditingId(null)
    setEditOpen(true)
  }

  const openEdit = (row: SalOrderRow) => {
    setEditingId(row.id)
    setEditOpen(true)
  }

  const columns: ColumnsType<SalOrderRow> = [
    { title: '订单号', dataIndex: 'orderNo', width: 150 },
    { title: '客户', dataIndex: 'customerName', width: 160, ellipsis: true },
    { title: '仓库', dataIndex: 'warehouseName', width: 110, ellipsis: true },
    { title: '订单日期', dataIndex: 'orderDate', width: 110 },
    {
      title: '数量',
      dataIndex: 'totalQty',
      width: 90,
      align: 'right',
      render: (v: number) => v?.toFixed?.(2) ?? '-',
    },
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
      width: 96,
      render: (v: number) => {
        const s = ORDER_STATUS[v]
        return s ? <Tag color={s.color}>{s.label}</Tag> : <Tag>{v}</Tag>
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 160 },
    {
      title: '操作',
      key: 'action',
      width: 230,
      render: (_, row) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => setDetailId(row.id)}>
            详情
          </Button>
          {row.status === 0 && (
            <HasPerm code="sales:order:edit">
              <Button type="link" size="small" onClick={() => openEdit(row)}>
                编辑
              </Button>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="sales:order:audit">
              <Popconfirm title="审核后将做信用额度校验，确认审核？" onConfirm={() => handleAudit(row.id)}>
                <Button type="link" size="small">
                  审核
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {(row.status === 10 || row.status === 20) && (
            <HasPerm code="sales:order:audit">
              <Popconfirm title="关闭后剩余数量不可再出库，确认关闭？" onConfirm={() => handleClose(row.id)}>
                <Button type="link" size="small" danger>
                  关闭
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="sales:order:delete">
            <Popconfirm title="确认删除该草稿订单？" onConfirm={() => handleDelete(row.id)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
            </HasPerm>
          )}
          {row.status !== 0 && (
            <HasPerm code="sales:order:list">
              <Button
                type="link"
                size="small"
                icon={<PrinterOutlined />}
                onClick={() => openPrint('sal_order', row.id)}
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
        销售订单
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Input.Search
            placeholder="订单号 / 客户名称"
            style={{ width: 240 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setKeyword(v)
            }}
          />
          <Select
            style={{ width: 130 }}
            allowClear
            placeholder="状态筛选"
            options={Object.entries(ORDER_STATUS).map(([v, s]) => ({ value: Number(v), label: s.label }))}
            onChange={(v) => {
              setPageNum(1)
              setStatus(v)
            }}
          />
          <HasPerm code="sales:order:add">
          <Button type="primary" onClick={openCreate}>
            新增订单
          </Button>
          </HasPerm>
        </Space>

        <Table<SalOrderRow>
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

      {/* 详情 Drawer */}
      <OrderDetailDrawer id={detailId} onClose={() => setDetailId(null)} />

      {/* 新增/编辑 Modal */}
      <OrderFormModal
        open={editOpen}
        editingId={editingId}
        onClose={() => setEditOpen(false)}
        onSuccess={invalidate}
      />
    </div>
  )
}

/** 订单详情 Drawer：表头 Descriptions + 明细 Table（含剩余可出数量） */
function OrderDetailDrawer({ id, onClose }: { id: number | null; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['sales', 'orders', 'detail', id],
    queryFn: () => getOrder(id as number),
    enabled: id != null,
  })

  const lineColumns: ColumnsType<SalOrderDetailRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 120 },
    { title: '物料名称', dataIndex: 'materialName', ellipsis: true },
    { title: '规格', dataIndex: 'materialSpec', width: 140, ellipsis: true },
    { title: '数量', dataIndex: 'qty', width: 90, align: 'right' },
    { title: '单价', dataIndex: 'price', width: 90, align: 'right' },
    { title: '金额', dataIndex: 'amount', width: 110, align: 'right' },
    { title: '已出库', dataIndex: 'outQty', width: 90, align: 'right' },
    { title: '剩余可出', dataIndex: 'remainingQty', width: 90, align: 'right' },
  ]

  const order = data?.order
  const statusMeta = order ? ORDER_STATUS[order.status] : undefined

  return (
    <Drawer open={id != null} onClose={onClose} width={760} title="销售订单详情">
      {isLoading || !order ? (
        <div style={{ padding: 24, textAlign: 'center', color: '#999' }}>加载中…</div>
      ) : (
        <>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="订单号">{order.orderNo}</Descriptions.Item>
            <Descriptions.Item label="状态">
              {statusMeta ? <Tag color={statusMeta.color}>{statusMeta.label}</Tag> : order.status}
            </Descriptions.Item>
            <Descriptions.Item label="客户">
              {order.customerCode ? `${order.customerCode} ` : ''}
              {order.customerName ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="仓库">{order.warehouseName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="订单日期">{order.orderDate}</Descriptions.Item>
            <Descriptions.Item label="金额">{Number(order.totalAmount).toFixed(2)}</Descriptions.Item>
            <Descriptions.Item label="数量">{Number(order.totalQty).toFixed(2)}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{order.createTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="审核时间">{order.auditTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="备注" span={2}>
              {order.remark || '-'}
            </Descriptions.Item>
          </Descriptions>

          <Title level={5} style={{ margin: '16px 0 8px' }}>
            明细行
          </Title>
          <Table<SalOrderDetailRow>
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

/** 新增/编辑订单 Modal（编辑仅草稿可进） */
function OrderFormModal({
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
  const [form] = Form.useForm<OrderFormValues>()

  // V131：表头客户 + 行物料/数量联动定价引擎参照
  const watchedCustomerId = Form.useWatch('customerId', form)
  const watchedDetails = Form.useWatch('details', form)

  // 下拉数据：客户/仓库/物料（basedata 未就绪时为空列表，页面仍可打开）
  const { data: customers = [] } = useQuery({
    queryKey: ['sales', 'options', 'customers'],
    queryFn: getCustomerOptions,
    enabled: open,
  })
  const { data: warehouses = [] } = useQuery({
    queryKey: ['sales', 'options', 'warehouses'],
    queryFn: getWarehouseOptions,
    enabled: open,
  })
  const { data: materials = [] } = useQuery({
    queryKey: ['sales', 'options', 'materials'],
    queryFn: getMaterialOptions,
    enabled: open,
  })

  // 编辑时加载原单回填
  const { data: origin } = useQuery({
    queryKey: ['sales', 'orders', 'detail', editingId],
    queryFn: () => getOrder(editingId as number),
    enabled: open && editingId != null,
  })

  const afterOpenChange = (visible: boolean) => {
    if (!visible) return
    if (editingId != null && origin) {
      form.setFieldsValue({
        customerId: origin.order.customerId,
        warehouseId: origin.order.warehouseId,
        orderDate: dayjs(origin.order.orderDate),
        remark: origin.order.remark ?? undefined,
        details: origin.details.map((d) => ({
          materialId: d.materialId,
          qty: Number(d.qty),
          price: Number(d.price),
        })),
      })
    } else {
      form.resetFields()
      form.setFieldsValue({ orderDate: dayjs(), details: [{ qty: 1, price: 0 }] })
    }
  }

  const handleSubmit = async (values: OrderFormValues) => {
    const payload: SalOrderFormPayload = {
      customerId: values.customerId,
      warehouseId: values.warehouseId,
      orderDate: values.orderDate.format('YYYY-MM-DD'),
      remark: values.remark,
      details: values.details.map((d) => ({
        materialId: d.materialId,
        qty: d.qty,
        price: d.price,
        remark: d.remark,
      })),
    }
    try {
      if (editingId != null) {
        await updateOrder(editingId, payload)
        message.success('修改成功')
      } else {
        await createOrder(payload)
        message.success('创建成功（草稿）')
      }
      onSuccess()
      onClose()
    } catch {
      /* 错误提示由请求拦截器统一处理 */
    }
  }

  return (
    <Modal
      open={open}
      title={editingId != null ? '编辑销售订单（草稿）' : '新增销售订单'}
      width={860}
      onCancel={onClose}
      onOk={() => form.submit()}
      destroyOnClose
      afterOpenChange={afterOpenChange}
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit}>
        <Space size="middle" style={{ display: 'flex' }} wrap>
          <Form.Item
            name="customerId"
            label="客户"
            rules={[{ required: true, message: '请选择客户' }]}
            style={{ minWidth: 260 }}
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
            style={{ minWidth: 200 }}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择仓库"
              options={warehouses.map((w) => ({ value: w.id, label: w.name }))}
            />
          </Form.Item>
          <Form.Item
            name="orderDate"
            label="订单日期"
            rules={[{ required: true, message: '请选择日期' }]}
          >
            <DatePicker style={{ width: 160 }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
          </Form.Item>
        </Space>

        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={1} maxLength={500} placeholder="选填" />
        </Form.Item>

        <Form.Item label="明细行" required style={{ marginBottom: 0 }}>
          <Form.List name="details" initialValue={[{}]}>
            {(fields, { add, remove }) => (
              <>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" style={{ display: 'flex', marginBottom: 4 }}>
                    <Form.Item
                      name={[field.name, 'materialId']}
                      rules={[{ required: true, message: '请选择物料' }]}
                      style={{ marginBottom: 0, minWidth: 280 }}
                    >
                      <Select
                        showSearch
                        optionFilterProp="label"
                        placeholder="物料"
                        options={materials.map((m) => ({
                          value: m.id,
                          label: `${m.code} ${m.name}`,
                        }))}
                      />
                    </Form.Item>
                    <PriceRefPopover
                      mode="sales"
                      customerId={watchedCustomerId}
                      materialId={watchedDetails?.[field.name]?.materialId}
                      qty={watchedDetails?.[field.name]?.qty}
                    />
                    <Form.Item
                      name={[field.name, 'qty']}
                      rules={[{ required: true, message: '数量' }]}
                      style={{ marginBottom: 0 }}
                    >
                      <InputNumber min={0.0001} precision={4} placeholder="数量" style={{ width: 110 }} />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'price']}
                      rules={[{ required: true, message: '单价' }]}
                      style={{ marginBottom: 0 }}
                    >
                      <InputNumber min={0} precision={2} placeholder="单价" style={{ width: 110 }} />
                    </Form.Item>
                    <Button type="link" danger size="small" onClick={() => remove(field.name)}>
                      删除
                    </Button>
                  </Space>
                ))}
                <Button type="dashed" block onClick={() => add({ qty: 1, price: 0 })}>
                  + 添加明细行
                </Button>
              </>
            )}
          </Form.List>
        </Form.Item>
      </Form>
    </Modal>
  )
}
