import HasPerm from '@/components/HasPerm'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import StatusTag from '@/components/StatusTag'
import { useDebounce } from '@/hooks/useDebounce'
import { useSubmitting } from '@/hooks/useSubmitting'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Descriptions, Drawer, Empty, Form, Input, InputNumber, DatePicker, Modal, Popconfirm, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { useState } from 'react'
import {
  QUOTATION_STATUS,
  createQuotation,
  deleteQuotation,
  getCustomerOptions,
  getMaterialOptions,
  getQuotation,
  getQuotationMargin,
  getWarehouseOptions,
  quotationToOrder,
  submitQuotation,
  updateQuotation,
  voidQuotation,
  type SalQuotationFormPayload,
  type SalQuotationItemRow,
  type SalQuotationRow,
} from '@/api/sales'
// V131 价格中心：定价引擎参照（只读展示，不影响提交逻辑）
import PriceRefPopover from '@/pages/basedata/prices/PriceRefPopover'

/** 报价单表单值（日期用 Dayjs 承接，提交前格式化） */
interface QuotationFormValues {
  customerId: number
  quoteDate: Dayjs
  validUntil: Dayjs
  discountRate?: number | null
  remark?: string
  items: { materialId: number; qty: number; price: number; remark?: string }[]
}

/**
 * 销售报价单页：列表 + 明细 Drawer（含毛利率红色警戒 + 转订单）+ 新增/编辑 Modal。
 * 状态机：草稿可改/可删/可提交；已报价可转订单（过期不可转）/可作废；
 * 行金额 = 数量 × 报价单价 × (1 − 整单折扣率)。
 */
export default function QuotationList() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebounce(keyword)
  const [status, setStatus] = useState<number | undefined>()
  const [customerId, setCustomerId] = useState<number | undefined>()

  const [detailId, setDetailId] = useState<number | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [toOrderQuotationId, setToOrderQuotationId] = useState<number | null>(null)

  const { data: customerOptions = [] } = useQuery({
    queryKey: ['sales', 'options', 'customers'],
    queryFn: getCustomerOptions,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['sales', 'quotations'] })

  const submitQuot = useSubmitting((id: number) => submitQuotation(id), {
    successText: '已提交报价',
    onSuccess: invalidate,
  })
  const voidQuot = useSubmitting((id: number) => voidQuotation(id), {
    successText: '已作废',
    onSuccess: invalidate,
  })
  const deleteQuot = useSubmitting((id: number) => deleteQuotation(id), {
    successText: '已删除',
    onSuccess: invalidate,
  })

  const columns: ColumnsType<SalQuotationRow> = [
    { title: '报价单号', dataIndex: 'quotationNo', width: 150 },
    { title: '客户', dataIndex: 'customerName', width: 150, ellipsis: true },
    { title: '报价日期', dataIndex: 'quoteDate', width: 105 },
    { title: '有效期至', dataIndex: 'validUntil', width: 105 },
    {
      title: '整单折扣',
      dataIndex: 'discountRate',
      width: 90,
      align: 'right',
      render: (v: number | null) => (v == null ? '-' : `${v}%`),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 96,
      render: (v: number) => {
        const s = QUOTATION_STATUS[v]
        return s ? <StatusTag value={s.label} semantic={s.semantic} /> : <StatusTag value={v} />
      },
    },
    {
      title: '转出订单',
      dataIndex: 'orderNo',
      width: 150,
      render: (v: string | null) => v ?? '-',
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
            <HasPerm code="sales:quotation:edit">
              <Button type="link" size="small" onClick={() => openEdit(row)}>
                编辑
              </Button>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="sales:quotation:audit">
              <Popconfirm title="提交后报价生效，确认提交报价？" onConfirm={() => submitQuot.run(row.id)}>
                <Button type="link" size="small" loading={submitQuot.loading}>
                  提交报价
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 10 && (
            <HasPerm code="sales:quotation:toOrder">
              <Button type="link" size="small" onClick={() => setToOrderQuotationId(row.id)}>
                转订单
              </Button>
            </HasPerm>
          )}
          {(row.status === 0 || row.status === 10) && (
            <HasPerm code="sales:quotation:audit">
              <Popconfirm title="作废后不可恢复，确认作废该报价单？" onConfirm={() => voidQuot.run(row.id)}>
                <Button type="link" size="small" danger loading={voidQuot.loading}>
                  作废
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="sales:quotation:delete">
              <Popconfirm title="确认删除该草稿报价单？" onConfirm={() => deleteQuot.run(row.id)}>
                <Button type="link" size="small" danger loading={deleteQuot.loading}>
                  删除
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  const openEdit = (row: SalQuotationRow) => {
    setEditingId(row.id)
    setEditOpen(true)
  }

  const resetFilters = () => {
    setKeyword('')
    setStatus(undefined)
    setCustomerId(undefined)
  }

  const toolbar = (
    <>
      <Input.Search
        placeholder="报价单号 / 客户名称"
        style={{ width: 220 }}
        allowClear
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
      />
      <Select
        style={{ width: 180 }}
        allowClear
        showSearch
        optionFilterProp="label"
        placeholder="客户筛选"
        options={customerOptions.map((c) => ({ value: c.id, label: `${c.code} ${c.name}` }))}
        value={customerId}
        onChange={(v) => setCustomerId(v)}
      />
      <Select
        style={{ width: 120 }}
        allowClear
        placeholder="状态筛选"
        options={Object.entries(QUOTATION_STATUS).map(([v, s]) => ({ value: Number(v), label: s.label }))}
        value={status}
        onChange={(v) => setStatus(v)}
      />
      <Button onClick={resetFilters}>重置</Button>
      <HasPerm code="sales:quotation:add">
        <Button
          type="primary"
          onClick={() => {
            setEditingId(null)
            setEditOpen(true)
          }}
        >
          新增报价单
        </Button>
      </HasPerm>
    </>
  )

  return (
    <div className="erp-page">
      <PageHeader title="销售报价单" subtitle="报价 → 转销售订单；明细含成本参考与毛利率警戒（低于 20% 红色标记）" />
      <PageTable<SalQuotationRow>
        url="/sales/quotations"
        queryKey={['sales', 'quotations', debouncedKeyword, customerId, status]}
        params={{ keyword: debouncedKeyword || undefined, customerId, status }}
        columns={columns}
        toolbar={toolbar}
      />

      {/* 详情 Drawer：表头 + 明细（含成本/毛利率警戒列）+ 转订单入口 */}
      <QuotationDetailDrawer id={detailId} onClose={() => setDetailId(null)} onToOrder={setToOrderQuotationId} />

      {/* 新增/编辑 Modal */}
      <QuotationFormModal
        open={editOpen}
        editingId={editingId}
        onClose={() => setEditOpen(false)}
        onSuccess={invalidate}
      />

      {/* 转销售订单 Modal */}
      <ToOrderModal
        quotationId={toOrderQuotationId}
        onClose={() => setToOrderQuotationId(null)}
        onSuccess={invalidate}
      />
    </div>
  )
}

/** 报价单详情 Drawer：明细行合并毛利率视图（(报价−成本)/报价，低于警戒线红色） */
function QuotationDetailDrawer({
  id,
  onClose,
  onToOrder,
}: {
  id: number | null
  onClose: () => void
  onToOrder: (quotationId: number) => void
}) {
  const { data, isLoading } = useQuery({
    queryKey: ['sales', 'quotations', 'detail', id],
    queryFn: () => getQuotation(id as number),
    enabled: id != null,
  })
  const { data: marginView } = useQuery({
    queryKey: ['sales', 'quotations', 'margin', id],
    queryFn: () => getQuotationMargin(id as number),
    enabled: id != null,
  })

  const marginByItemId = new Map((marginView?.rows ?? []).map((r) => [r.itemId, r]))

  const lineColumns: ColumnsType<SalQuotationItemRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 110 },
    { title: '物料名称', dataIndex: 'materialName', ellipsis: true },
    { title: '规格', dataIndex: 'materialSpec', width: 120, ellipsis: true },
    { title: '数量', dataIndex: 'qty', width: 80, align: 'right' },
    { title: '报价单价', dataIndex: 'price', width: 90, align: 'right' },
    {
      title: '成本参考',
      dataIndex: 'costPrice',
      width: 90,
      align: 'right',
      render: (v: number | null) => (v == null ? '-' : Number(v).toFixed(2)),
    },
    { title: '行金额(折后)', dataIndex: 'amount', width: 100, align: 'right' },
    {
      title: '毛利率',
      key: 'margin',
      width: 110,
      align: 'right',
      render: (_, row) => {
        const m = marginByItemId.get(row.id)
        if (!m || m.marginRate == null) return '-'
        return m.warn ? (
          <Space size={4}>
            <span style={{ color: '#cf1322', fontWeight: 600 }}>{m.marginRate.toFixed(2)}%</span>
            <Tag color="error">警戒</Tag>
          </Space>
        ) : (
          <span>{m.marginRate.toFixed(2)}%</span>
        )
      },
    },
  ]

  const quotation = data?.quotation
  const statusMeta = quotation ? QUOTATION_STATUS[quotation.status] : undefined

  return (
    <Drawer
      open={id != null}
      onClose={onClose}
      width={860}
      title="报价单详情"
      extra={
        quotation?.status === 10 ? (
          <HasPerm code="sales:quotation:toOrder">
            <Button type="primary" size="small" onClick={() => onToOrder(quotation.id)}>
              转销售订单
            </Button>
          </HasPerm>
        ) : null
      }
    >
      {isLoading || !quotation ? (
        <div style={{ padding: 24, textAlign: 'center', color: '#999' }}>加载中…</div>
      ) : (
        <>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="报价单号">{quotation.quotationNo}</Descriptions.Item>
            <Descriptions.Item label="状态">
              {statusMeta ? <StatusTag value={statusMeta.label} semantic={statusMeta.semantic} /> : quotation.status}
            </Descriptions.Item>
            <Descriptions.Item label="客户">
              {quotation.customerCode ? `${quotation.customerCode} ` : ''}
              {quotation.customerName ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="整单折扣">
              {quotation.discountRate == null ? '-' : `${quotation.discountRate}%`}
            </Descriptions.Item>
            <Descriptions.Item label="报价日期">{quotation.quoteDate}</Descriptions.Item>
            <Descriptions.Item label="有效期至">
              <span style={quotation.status === 30 ? { color: '#cf1322' } : undefined}>
                {quotation.validUntil}
                {quotation.status === 30 ? '（已过期）' : ''}
              </span>
            </Descriptions.Item>
            <Descriptions.Item label="转出订单">{quotation.orderNo ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{quotation.createTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="备注" span={2}>
              {quotation.remark || '-'}
            </Descriptions.Item>
          </Descriptions>

          <div style={{ margin: '16px 0 8px', fontWeight: 600 }}>
            明细行（毛利率 = (报价 − 成本)/报价，低于 {marginView?.warnThreshold ?? 20}% 红色警戒）
          </div>
          {data?.items?.length ? (
            <Table<SalQuotationItemRow>
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

/** 新增/编辑报价单 Modal（编辑仅草稿可进；成本参考由后端从物料档案快照，不在此录入） */
function QuotationFormModal({
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
  const [form] = Form.useForm<QuotationFormValues>()

  // V131：表头客户 + 行物料/数量联动定价引擎参照
  const watchedCustomerId = Form.useWatch('customerId', form)
  const watchedItems = Form.useWatch('items', form)

  const { data: customers = [] } = useQuery({
    queryKey: ['sales', 'options', 'customers'],
    queryFn: getCustomerOptions,
    enabled: open,
  })
  const { data: materials = [] } = useQuery({
    queryKey: ['sales', 'options', 'materials'],
    queryFn: getMaterialOptions,
    enabled: open,
  })
  const { data: origin } = useQuery({
    queryKey: ['sales', 'quotations', 'detail', editingId],
    queryFn: () => getQuotation(editingId as number),
    enabled: open && editingId != null,
  })

  const afterOpenChange = (visible: boolean) => {
    if (!visible) return
    if (editingId != null && origin) {
      form.setFieldsValue({
        customerId: origin.quotation.customerId,
        quoteDate: dayjs(origin.quotation.quoteDate),
        validUntil: dayjs(origin.quotation.validUntil),
        discountRate: origin.quotation.discountRate ?? undefined,
        remark: origin.quotation.remark ?? undefined,
        items: origin.items.map((d) => ({
          materialId: d.materialId,
          qty: Number(d.qty),
          price: Number(d.price),
          remark: d.remark ?? undefined,
        })),
      })
    } else {
      form.resetFields()
      form.setFieldsValue({
        quoteDate: dayjs(),
        validUntil: dayjs().add(30, 'day'),
        items: [{ qty: 1, price: 0 }],
      })
    }
  }

  const submit = useSubmitting(
    async (values: QuotationFormValues) => {
      const payload: SalQuotationFormPayload = {
        customerId: values.customerId,
        quoteDate: values.quoteDate.format('YYYY-MM-DD'),
        validUntil: values.validUntil.format('YYYY-MM-DD'),
        discountRate: values.discountRate ?? null,
        remark: values.remark,
        items: values.items.map((d) => ({
          materialId: d.materialId,
          qty: d.qty,
          price: d.price,
          remark: d.remark,
        })),
      }
      if (editingId != null) {
        await updateQuotation(editingId, payload)
      } else {
        await createQuotation(payload)
      }
    },
    {
      successText: editingId != null ? '修改成功' : '已创建报价单（草稿）',
      onSuccess: () => {
        onSuccess()
        onClose()
      },
    },
  )

  return (
    <Modal
      open={open}
      title={editingId != null ? '编辑报价单（草稿）' : '新增报价单'}
      width={860}
      onCancel={onClose}
      onOk={() => form.submit()}
      okButtonProps={{ loading: submit.loading }}
      destroyOnClose
      afterOpenChange={afterOpenChange}
    >
      <Form form={form} layout="vertical" onFinish={(v) => submit.run(v)}>
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
            name="quoteDate"
            label="报价日期"
            rules={[{ required: true, message: '请选择报价日期' }]}
          >
            <DatePicker style={{ width: 150 }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
          </Form.Item>
          <Form.Item
            name="validUntil"
            label="有效期至"
            rules={[{ required: true, message: '请选择有效期' }]}
          >
            <DatePicker style={{ width: 150 }} />
          </Form.Item>
          <Form.Item name="discountRate" label="整单折扣率%" style={{ marginBottom: 0 }}>
            <InputNumber min={0} max={100} precision={2} placeholder="空=不打折" style={{ width: 130 }} />
          </Form.Item>
        </Space>

        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={1} maxLength={500} placeholder="选填" />
        </Form.Item>

        <Form.Item label="明细行（行金额 = 数量 × 单价 × (1 − 整单折扣率)）" required style={{ marginBottom: 0 }}>
          <Form.List name="items" initialValue={[{}]}>
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
                        options={materials.map((m) => ({ value: m.id, label: `${m.code} ${m.name}` }))}
                      />
                    </Form.Item>
                    <PriceRefPopover
                      mode="sales"
                      customerId={watchedCustomerId}
                      materialId={watchedItems?.[field.name]?.materialId}
                      qty={watchedItems?.[field.name]?.qty}
                    />
                    <Form.Item
                      name={[field.name, 'qty']}
                      rules={[{ required: true, message: '请输入数量' }]}
                      style={{ marginBottom: 0 }}
                    >
                      <InputNumber min={0.0001} precision={4} placeholder="数量" style={{ width: 110 }} />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'price']}
                      rules={[{ required: true, message: '请输入报价单价' }]}
                      style={{ marginBottom: 0 }}
                    >
                      <InputNumber min={0} precision={2} placeholder="报价单价" style={{ width: 120 }} />
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

/** 转销售订单 Modal：选仓库 → 下推（单价 = 报价 × (1 − 折扣率)，生成草稿订单） */
function ToOrderModal({
  quotationId,
  onClose,
  onSuccess,
}: {
  quotationId: number | null
  onClose: () => void
  onSuccess: () => void
}) {
  const [form] = Form.useForm<{ warehouseId?: number }>()

  const { data: warehouses = [] } = useQuery({
    queryKey: ['sales', 'options', 'warehouses'],
    queryFn: getWarehouseOptions,
    enabled: quotationId != null,
  })

  const submit = useSubmitting(
    async (values: { warehouseId?: number }) => {
      if (quotationId == null) return
      await quotationToOrder(quotationId, { warehouseId: values.warehouseId })
    },
    {
      successText: '已生成销售订单（草稿），可前往销售订单列表审核',
      onSuccess: () => {
        onSuccess()
        onClose()
      },
    },
  )

  const afterOpenChange = (visible: boolean) => {
    if (visible) {
      form.resetFields()
      if (warehouses.length > 0) form.setFieldValue('warehouseId', warehouses[0].id)
    }
  }

  return (
    <Modal
      open={quotationId != null}
      title="报价单下推销售订单"
      width={520}
      onCancel={onClose}
      onOk={() => form.submit()}
      okButtonProps={{ loading: submit.loading }}
      okText="下推"
      destroyOnClose
      afterOpenChange={afterOpenChange}
    >
      <Form form={form} layout="vertical" onFinish={(v) => submit.run(v)}>
        <Form.Item
          name="warehouseId"
          label="发货仓库"
          extra="订单单价 = 报价单价 × (1 − 整单折扣率)；订单备注自动记录「报价单生成」"
          rules={[{ required: true, message: '请选择发货仓库' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="选择发货仓库"
            options={warehouses.map((w) => ({ value: w.id, label: w.name }))}
          />
        </Form.Item>
      </Form>
    </Modal>
  )
}
