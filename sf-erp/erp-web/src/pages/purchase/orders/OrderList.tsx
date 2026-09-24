import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, DeleteOutlined, PrinterOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { usePrint } from '@/hooks/usePrint'
import {
  orderApi,
  type DocPageParams,
  type PurOrderDetail,
  type PurOrderDetailRow,
  type PurOrderRow,
  type PurOrderSavePayload,
} from '@/api/purchase'
import {
  ORDER_STATUS_OPTIONS,
  OrderStatusTag,
  fmtAmount,
  fmtQty,
  lineAmount,
  optionLabel,
  useMaterialOptions,
  useSupplierOptions,
  useWarehouseOptions,
} from '../shared'
// V131 价格中心：录单价格参照（只读展示，不影响提交逻辑）
import PriceRefPopover from '@/pages/basedata/prices/PriceRefPopover'

const { Title, Text } = Typography

/** 列表筛选状态（日期区间用 Dayjs，提交参数时再格式化） */
interface FilterState {
  keyword: string
  supplierId?: number
  status?: number
  range?: [Dayjs, Dayjs] | null
}

/**
 * 采购订单页：单据列表（参照 UserList 模板）+ Drawer 详情（主子表）
 * + Drawer 新建/修改表单（Form.List 主子表录入，金额实时预览、服务端计算为准）。
 */
export default function OrderList() {
  const [filter, setFilter] = useState<FilterState>({ keyword: '' })
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [formState, setFormState] = useState<{ open: boolean; editing: PurOrderDetail | null }>({
    open: false,
    editing: null,
  })

  const suppliers = useSupplierOptions()

  const params: DocPageParams = {
    pageNum,
    pageSize,
    keyword: filter.keyword || undefined,
    supplierId: filter.supplierId,
    status: filter.status,
    dateBegin: filter.range?.[0]?.format('YYYY-MM-DD'),
    dateEnd: filter.range?.[1]?.format('YYYY-MM-DD'),
  }

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['purchase', 'orders', params],
    queryFn: () => orderApi.page(params),
  })

  const queryClient = useQueryClient()
  const { openPrint } = usePrint()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['purchase', 'orders'] })

  const auditMut = useMutation({ mutationFn: orderApi.audit, onSuccess: () => { message.success('审核成功'); invalidate() } })
  const closeMut = useMutation({ mutationFn: orderApi.close, onSuccess: () => { message.success('已关闭'); invalidate() } })
  const deleteMut = useMutation({ mutationFn: orderApi.remove, onSuccess: () => { message.success('已删除'); invalidate() } })

  /** 打开修改：先取详情，成功后再开表单（保证表单一次性拿到完整主子表） */
  const openEdit = async (id: number) => {
    try {
      const detail = await orderApi.get(id)
      setFormState({ open: true, editing: detail })
    } catch {
      /* 错误提示由 request 拦截器统一处理 */
    }
  }

  const columns: ColumnsType<PurOrderRow> = [
    { title: '订单号', dataIndex: 'orderNo', width: 170 },
    { title: '订单日期', dataIndex: 'orderDate', width: 110 },
    { title: '供应商', dataIndex: 'supplierName', width: 160, ellipsis: true },
    { title: '入库仓库', dataIndex: 'warehouseName', width: 130, ellipsis: true },
    {
      title: '价税合计',
      dataIndex: 'totalAmount',
      width: 120,
      align: 'right',
      render: (v: number) => <Text strong>{fmtAmount(v)}</Text>,
    },
    { title: '状态', dataIndex: 'status', width: 100, render: (v: PurOrderRow['status']) => <OrderStatusTag status={v} /> },
    { title: '审核时间', dataIndex: 'auditTime', width: 165, render: (v: string | null) => v ?? '-' },
    {
      title: '操作',
      key: 'actions',
      width: 250,
      fixed: 'right',
      render: (_, row) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => setDetailId(row.id)}>
            详情
          </Button>
          {row.status === 0 && (
            <HasPerm code="purchase:order:edit">
              <Button type="link" size="small" onClick={() => void openEdit(row.id)}>
                修改
              </Button>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="purchase:order:audit">
              <Popconfirm title="确认审核该订单？" onConfirm={() => auditMut.mutate(row.id)}>
                <Button type="link" size="small">
                  审核
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {(row.status === 10 || row.status === 20) && (
            <HasPerm code="purchase:order:audit">
              <Popconfirm title="关闭后不可再入库，确认关闭？" onConfirm={() => closeMut.mutate(row.id)}>
                <Button type="link" size="small" danger>
                  关闭
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="purchase:order:delete">
            <Popconfirm title="确认删除该草稿订单？" onConfirm={() => deleteMut.mutate(row.id)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
            </HasPerm>
          )}
          {row.status !== 0 && (
            <HasPerm code="purchase:order:list">
              <Button
                type="link"
                size="small"
                icon={<PrinterOutlined />}
                onClick={() => openPrint('pur_order', row.id)}
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
        采购订单
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="订单号模糊搜索"
            style={{ width: 220 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setFilter((f) => ({ ...f, keyword: v }))
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
              setFilter((f) => ({ ...f, supplierId: v }))
            }}
          />
          <Select
            placeholder="状态"
            style={{ width: 130 }}
            allowClear
            options={ORDER_STATUS_OPTIONS}
            onChange={(v) => {
              setPageNum(1)
              setFilter((f) => ({ ...f, status: v }))
            }}
          />
          <DatePicker.RangePicker
            onChange={(v) => {
              setPageNum(1)
              setFilter((f) => ({ ...f, range: v as [Dayjs, Dayjs] | null }))
            }}
          />
          <HasPerm code="purchase:order:add">
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setFormState({ open: true, editing: null })}
            >
              新建订单
            </Button>
          </HasPerm>
        </Space>

        <Table<PurOrderRow>
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

      {/* 详情抽屉：条件渲染保证每次打开重新拉取 */}
      {detailId != null && <OrderDetailDrawer id={detailId} onClose={() => setDetailId(null)} />}

      {/* 新建/修改表单抽屉：key 保证不同单据重新初始化表单 */}
      {formState.open && (
        <OrderFormDrawer
          key={formState.editing?.id ?? 'new'}
          editing={formState.editing}
          onClose={() => setFormState({ open: false, editing: null })}
        />
      )}
    </div>
  )
}

/** 订单明细表格列（详情抽屉复用） */
const DETAIL_COLUMNS: ColumnsType<PurOrderDetailRow> = [
  { title: '物料编码', dataIndex: 'materialCode', width: 120 },
  { title: '物料名称', dataIndex: 'materialName', width: 150, ellipsis: true },
  { title: '规格', dataIndex: 'spec', width: 110, ellipsis: true },
  { title: '单位', dataIndex: 'unit', width: 70 },
  { title: '数量', dataIndex: 'qty', width: 100, align: 'right', render: fmtQty },
  { title: '含税单价', dataIndex: 'price', width: 100, align: 'right', render: (v: number) => v.toFixed(4) },
  { title: '税率%', dataIndex: 'taxRate', width: 80, align: 'right' },
  { title: '金额', dataIndex: 'amount', width: 110, align: 'right', render: (v: number) => <Text strong>{fmtAmount(v)}</Text> },
  { title: '交货日期', dataIndex: 'deliveryDate', width: 110, render: (v: string | null) => v ?? '-' },
  { title: '已入库', dataIndex: 'receivedQty', width: 100, align: 'right', render: fmtQty },
  { title: '备注', dataIndex: 'remark', ellipsis: true },
]

/** 采购订单详情抽屉（主表 Descriptions + 明细 Table） */
function OrderDetailDrawer({ id, onClose }: { id: number; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['purchase', 'order-detail', id],
    queryFn: () => orderApi.get(id),
  })

  return (
    <Drawer title={`采购订单详情 ${data?.orderNo ?? ''}`} width={1080} open onClose={onClose}>
      {isLoading || !data ? (
        <Card loading style={{ border: 'none' }} />
      ) : (
        <>
          <Descriptions bordered size="small" column={3}>
            <Descriptions.Item label="订单号">{data.orderNo}</Descriptions.Item>
            <Descriptions.Item label="状态"><OrderStatusTag status={data.status} /></Descriptions.Item>
            <Descriptions.Item label="订单日期">{data.orderDate}</Descriptions.Item>
            <Descriptions.Item label="供应商">{data.supplierName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="入库仓库">{data.warehouseName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="价税合计">
              <Text strong style={{ color: '#1668dc' }}>{fmtAmount(data.totalAmount)}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="审核时间">{data.auditTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{data.createTime}</Descriptions.Item>
            <Descriptions.Item label="备注">{data.remark ?? '-'}</Descriptions.Item>
          </Descriptions>

          <Title level={5} style={{ margin: '16px 0 8px' }}>
            订单明细
          </Title>
          <Table<PurOrderDetailRow>
            rowKey="id"
            size="small"
            columns={DETAIL_COLUMNS}
            dataSource={data.details ?? []}
            pagination={false}
            scroll={{ x: 1100 }}
            summary={() => (
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={7}>
                  <Text strong>合计</Text>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={1} align="right">
                  <Text strong style={{ color: '#1668dc' }}>{fmtAmount(data.totalAmount)}</Text>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={2} colSpan={3} />
              </Table.Summary.Row>
            )}
          />
        </>
      )}
    </Drawer>
  )
}

/** 表单值类型（DatePicker 存 Dayjs，提交时转换；字段可选以匹配 antd initialValues 局部填充） */
interface OrderFormValues {
  supplierId?: number
  warehouseId?: number
  orderDate?: Dayjs
  remark?: string
  details?: {
    materialId?: number
    qty?: number
    price?: number
    taxRate?: number
    deliveryDate?: Dayjs | null
    remark?: string
  }[]
}

/** 新建/修改采购订单抽屉表单 */
function OrderFormDrawer({ editing, onClose }: { editing: PurOrderDetail | null; onClose: () => void }) {
  const [form] = Form.useForm<OrderFormValues>()
  const queryClient = useQueryClient()
  const suppliers = useSupplierOptions()
  const warehouses = useWarehouseOptions()
  const materials = useMaterialOptions()
  const watchedDetails = Form.useWatch('details', form)
  // V131：表头供应商联动价格参照（合同价/最近价/历史）
  const watchedSupplierId = Form.useWatch('supplierId', form)

  const materialOptions = (materials.data ?? []).map((m) => ({
    value: m.id,
    label: optionLabel(m),
    spec: m.spec,
    unit: m.unit,
  }))

  const initialValues: OrderFormValues = editing
    ? {
        supplierId: editing.supplierId,
        warehouseId: editing.warehouseId ?? undefined,
        orderDate: dayjs(editing.orderDate),
        remark: editing.remark ?? undefined,
        details: editing.details.map((d) => ({
          materialId: d.materialId,
          qty: d.qty,
          price: d.price,
          taxRate: d.taxRate,
          deliveryDate: d.deliveryDate ? dayjs(d.deliveryDate) : undefined,
          remark: d.remark ?? undefined,
        })),
      }
    : {
        orderDate: dayjs(),
        details: [{ price: 0, taxRate: 13 }],
      }

  const total = (watchedDetails ?? []).reduce(
    (sum, d) => sum + lineAmount(d?.qty, d?.price),
    0,
  )

  const saveMut = useMutation({
    mutationFn: async (payload: PurOrderSavePayload): Promise<void> => {
      if (editing) {
        await orderApi.update(editing.id, payload)
      } else {
        await orderApi.create(payload)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '创建成功（草稿）')
      queryClient.invalidateQueries({ queryKey: ['purchase', 'orders'] })
      queryClient.invalidateQueries({ queryKey: ['purchase', 'order-detail'] })
      onClose()
    },
  })

  /** 提交：过滤未选物料的空行，日期转字符串 */
  const onFinish = (values: OrderFormValues) => {
    const details = (values.details ?? []).filter((d) => d.materialId != null && (d.qty ?? 0) > 0)
    if (details.length === 0) {
      message.warning('请至少录入一行有效明细（物料+数量）')
      return
    }
    saveMut.mutate({
      supplierId: values.supplierId!,
      warehouseId: values.warehouseId,
      orderDate: values.orderDate!.format('YYYY-MM-DD'),
      remark: values.remark,
      details: details.map((d) => ({
        materialId: d.materialId!,
        qty: d.qty!,
        price: d.price ?? 0,
        taxRate: d.taxRate ?? 0,
        deliveryDate: d.deliveryDate ? d.deliveryDate.format('YYYY-MM-DD') : undefined,
        remark: d.remark,
      })),
    })
  }

  return (
    <Drawer
      title={editing ? `修改采购订单 ${editing.orderNo}` : '新建采购订单'}
      width={1120}
      open
      onClose={onClose}
      footer={
        <Space style={{ display: 'flex', justifyContent: 'space-between' }}>
          <Text>
            价税合计：<Text strong style={{ color: '#1668dc', fontSize: 16 }}>{fmtAmount(total)}</Text>
          </Text>
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button type="primary" loading={saveMut.isPending} onClick={() => form.submit()}>
              提交
            </Button>
          </Space>
        </Space>
      }
    >
      <Form form={form} layout="vertical" initialValues={initialValues} onFinish={onFinish}>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name="supplierId" label="供应商" rules={[{ required: true, message: '请选择供应商' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="选择供应商"
                options={(suppliers.data ?? []).map((s) => ({ value: s.id, label: optionLabel(s) }))}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="warehouseId" label="默认入库仓库">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="选择仓库（可选）"
                options={(warehouses.data ?? []).map((w) => ({ value: w.id, label: optionLabel(w) }))}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="orderDate" label="订单日期" rules={[{ required: true, message: '请选择日期' }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>

        <Title level={5} style={{ margin: '4px 0 8px' }}>
          订单明细
        </Title>
        <Form.List name="details">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field, idx) => {
                const row = watchedDetails?.[idx]
                return (
                  <Row key={field.key} gutter={8} style={{ marginBottom: 4 }} align="middle">
                    <Col span={7}>
                      <div style={{ display: 'flex', gap: 4, alignItems: 'flex-start' }}>
                        <Form.Item
                          name={[field.name, 'materialId']}
                          rules={[{ required: true, message: '选择物料' }]}
                          style={{ marginBottom: 8, flex: 1 }}
                        >
                          <Select showSearch optionFilterProp="label" placeholder="物料" options={materialOptions} />
                        </Form.Item>
                        <PriceRefPopover
                          mode="purchase"
                          supplierId={watchedSupplierId}
                          materialId={row?.materialId}
                        />
                      </div>
                    </Col>
                    <Col span={3}>
                      <Form.Item
                        name={[field.name, 'qty']}
                        rules={[{ required: true, message: '数量' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <InputNumber min={0.0001} placeholder="数量" style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col span={3}>
                      <Form.Item
                        name={[field.name, 'price']}
                        rules={[{ required: true, message: '单价' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <InputNumber min={0} precision={4} placeholder="含税单价" style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col span={3}>
                      <Form.Item name={[field.name, 'taxRate']} style={{ marginBottom: 8 }}>
                        <InputNumber min={0} max={100} precision={2} placeholder="税率" style={{ width: '100%' }} addonAfter="%" />
                      </Form.Item>
                    </Col>
                    <Col span={4}>
                      <Form.Item name={[field.name, 'deliveryDate']} style={{ marginBottom: 8 }}>
                        <DatePicker placeholder="交货日期" style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col span={3} style={{ textAlign: 'right' }}>
                      <Text>{fmtAmount(lineAmount(row?.qty, row?.price))}</Text>
                    </Col>
                    <Col span={1}>
                      <DeleteOutlined
                        onClick={() => fields.length > 1 && remove(field.name)}
                        style={{ color: fields.length > 1 ? '#ff4d4f' : '#c0c4cc', cursor: fields.length > 1 ? 'pointer' : 'not-allowed' }}
                      />
                    </Col>
                  </Row>
                )
              })}
              <Button
                type="dashed"
                block
                icon={<PlusOutlined />}
                onClick={() => add({ price: 0, taxRate: 13 })}
                style={{ marginBottom: 12 }}
              >
                添加明细行
              </Button>
            </>
          )}
        </Form.List>

        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={500} showCount placeholder="订单备注" />
        </Form.Item>
      </Form>
    </Drawer>
  )
}
