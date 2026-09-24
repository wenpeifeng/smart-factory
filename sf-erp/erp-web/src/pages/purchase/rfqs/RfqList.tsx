import HasPerm from '@/components/HasPerm'
import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router-dom'
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
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import {
  requisitionApi,
  rfqApi,
  type RfqAwardPayload,
  type RfqComparison,
  type RfqDetail,
  type RfqItemRow,
  type RfqRow,
  type RfqSavePayload,
  type RfqSupplierRow,
  type RequisitionDetail,
} from '@/api/purchase'
import { fmtAmount, fmtQty, optionLabel, useMaterialOptions, useSupplierOptions } from '../shared'

const { Title, Text } = Typography

/** 询比价单状态 -> 文案/颜色 */
const RFQ_STATUS_MAP: Record<number, { text: string; color: string }> = {
  0: { text: '询价中', color: 'processing' },
  10: { text: '已定标', color: 'success' },
  30: { text: '已取消', color: 'default' },
}

const RFQ_STATUS_OPTIONS = Object.entries(RFQ_STATUS_MAP).map(([value, cfg]) => ({
  value: Number(value),
  label: cfg.text,
}))

/** 询比价供应商行状态 */
const RFQ_SUPPLIER_STATUS_MAP: Record<number, { text: string; color: string }> = {
  0: { text: '已邀请', color: 'default' },
  10: { text: '已报价', color: 'processing' },
  20: { text: '中标', color: 'success' },
}

function RfqStatusTag({ status }: { status: number }) {
  const cfg = RFQ_STATUS_MAP[status] ?? { text: `未知(${status})`, color: 'default' }
  return <Tag color={cfg.color}>{cfg.text}</Tag>
}

function RfqSupplierStatusTag({ status }: { status: number }) {
  const cfg = RFQ_SUPPLIER_STATUS_MAP[status] ?? { text: `未知(${status})`, color: 'default' }
  return <Tag color={cfg.color}>{cfg.text}</Tag>
}

/** 列表筛选状态 */
interface FilterState {
  keyword: string
  status?: number
}

/** 表单弹层状态：新建/修改抽屉 + 报价录入抽屉 + 比价抽屉 + 定标弹窗 */
interface FormOpenState {
  open: boolean
  editing: RfqDetail | null
  /** 从请购单跳转带入的预填充 */
  prefillReq: RequisitionDetail | null
}

/**
 * 询比价页（请购 -> 询比价 -> 采购订单链路中段）：
 * 列表 + 新建（可从请购带入明细，邀请 >=3 家供应商）+ 报价录入 Drawer
 * + 比价矩阵（物料×供应商，最低价高亮）+ 分项定标 Modal + 下推采购订单。
 */
export default function RfqList() {
  const [filter, setFilter] = useState<FilterState>({ keyword: '' })
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [formState, setFormState] = useState<FormOpenState>({ open: false, editing: null, prefillReq: null })
  const [quoteRfqId, setQuoteRfqId] = useState<number | null>(null)
  const [compareRfqId, setCompareRfqId] = useState<number | null>(null)
  const [awardRfqId, setAwardRfqId] = useState<number | null>(null)

  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const fromReqId = searchParams.get('from')

  const params = {
    pageNum,
    pageSize,
    keyword: filter.keyword || undefined,
    status: filter.status,
  }

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['purchase', 'rfqs', params],
    queryFn: () => rfqApi.page(params),
  })

  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['purchase', 'rfqs'] })

  const cancelMut = useMutation({
    mutationFn: rfqApi.cancel,
    onSuccess: () => {
      message.success('已取消')
      invalidate()
    },
  })
  const deleteMut = useMutation({
    mutationFn: rfqApi.remove,
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
  })
  const toOrderMut = useMutation({
    mutationFn: rfqApi.toOrders,
    onSuccess: (result) => {
      const nos = result.orders.map((o) => o.orderNo).join('、')
      message.success(`已生成 ${result.orders.length} 张采购订单草稿：${nos}`)
      invalidate()
    },
  })

  /** 从请购单跳转（/purchase/rfqs?from=id）：拉取请购详情预填充新建抽屉 */
  useEffect(() => {
    if (!fromReqId) return
    let cancelled = false
    void (async () => {
      try {
        const req = await requisitionApi.get(Number(fromReqId))
        if (!cancelled) {
          setFormState({ open: true, editing: null, prefillReq: req })
        }
      } catch {
        /* 拉取失败静默，用户可手动新建 */
      }
    })()
    return () => {
      cancelled = true
    }
  }, [fromReqId])

  /** 打开修改：先取详情再开表单 */
  const openEdit = async (id: number) => {
    try {
      const detail = await rfqApi.get(id)
      setFormState({ open: true, editing: detail, prefillReq: null })
    } catch {
      /* 错误提示由 request 拦截器统一处理 */
    }
  }

  const columns: ColumnsType<RfqRow> = [
    { title: '询比价单号', dataIndex: 'rfqNo', width: 170 },
    { title: '来源请购单', dataIndex: 'reqNo', width: 170, render: (v: string | null) => v ?? '独立询价' },
    { title: '截止日期', dataIndex: 'deadline', width: 110, render: (v: string | null) => v ?? '-' },
    { title: '状态', dataIndex: 'status', width: 100, render: (v: number) => <RfqStatusTag status={v} /> },
    { title: '定标时间', dataIndex: 'awardTime', width: 165, render: (v: string | null) => v ?? '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'actions',
      width: 330,
      fixed: 'right',
      render: (_, row) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => setDetailId(row.id)}>
            详情
          </Button>
          <Button type="link" size="small" onClick={() => setCompareRfqId(row.id)}>
            比价
          </Button>
          {row.status === 0 && (
            <HasPerm code="purchase:rfq:edit">
              <Button type="link" size="small" onClick={() => void openEdit(row.id)}>
                修改
              </Button>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="purchase:rfq:edit">
              <Button type="link" size="small" onClick={() => setQuoteRfqId(row.id)}>
                录报价
              </Button>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="purchase:rfq:award">
              <Button type="link" size="small" onClick={() => setAwardRfqId(row.id)}>
                定标
              </Button>
            </HasPerm>
          )}
          {row.status === 10 && (
            <HasPerm code="purchase:rfq:toOrder">
              <Popconfirm
                title="按定标供应商下推生成采购订单草稿？"
                onConfirm={() => toOrderMut.mutate(row.id)}
              >
                <Button type="link" size="small">
                  下推订单
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="purchase:rfq:edit">
              <Popconfirm title="确认取消该询比价单？" onConfirm={() => cancelMut.mutate(row.id)}>
                <Button type="link" size="small" danger>
                  取消
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="purchase:rfq:delete">
              <Popconfirm title="确认删除该询比价单？" onConfirm={() => deleteMut.mutate(row.id)}>
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
        询比价
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="询比价单号模糊搜索"
            style={{ width: 220 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setFilter((f) => ({ ...f, keyword: v }))
            }}
          />
          <Select
            placeholder="状态"
            style={{ width: 130 }}
            allowClear
            options={RFQ_STATUS_OPTIONS}
            onChange={(v) => {
              setPageNum(1)
              setFilter((f) => ({ ...f, status: v }))
            }}
          />
          <HasPerm code="purchase:rfq:add">
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setFormState({ open: true, editing: null, prefillReq: null })}
            >
              新建询比价
            </Button>
          </HasPerm>
        </Space>

        <Table<RfqRow>
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          scroll={{ x: 1300 }}
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

      {detailId != null && <RfqDetailDrawer id={detailId} onClose={() => setDetailId(null)} />}

      {formState.open && (
        <RfqFormDrawer
          key={formState.editing?.id ?? (formState.prefillReq?.id ? `from-${formState.prefillReq.id}` : 'new')}
          editing={formState.editing}
          prefillReq={formState.prefillReq}
          onClose={() => setFormState({ open: false, editing: null, prefillReq: null })}
          onCreated={() => {
            // 清掉 ?from= 预填充参数，避免刷新后再次弹窗
            if (fromReqId) {
              setSearchParams({}, { replace: true })
              navigate('/purchase/rfqs', { replace: true })
            }
          }}
        />
      )}

      {quoteRfqId != null && <QuoteDrawer id={quoteRfqId} onClose={() => setQuoteRfqId(null)} />}
      {compareRfqId != null && <ComparisonDrawer id={compareRfqId} onClose={() => setCompareRfqId(null)} />}
      {awardRfqId != null && <AwardModal id={awardRfqId} onClose={() => setAwardRfqId(null)} />}
    </div>
  )
}

/** 询比价详情抽屉（主表 + 供应商表 + 明细含定标/下推回写） */
function RfqDetailDrawer({ id, onClose }: { id: number; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['purchase', 'rfq-detail', id],
    queryFn: () => rfqApi.get(id),
  })

  const supplierName = (sid: number | null): string => {
    if (sid == null) return '-'
    const s = data?.suppliers.find((x) => x.supplierId === sid)
    return s?.supplierName ?? String(sid)
  }

  const supplierColumns: ColumnsType<RfqSupplierRow> = [
    { title: '供应商', dataIndex: 'supplierName', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 90, render: (v: number) => <RfqSupplierStatusTag status={v} /> },
    {
      title: '报价金额合计',
      dataIndex: 'quoteAmount',
      width: 130,
      align: 'right',
      render: (v: number) => fmtAmount(v),
    },
  ]

  const itemColumns: ColumnsType<RfqItemRow> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 110 },
    { title: '物料名称', dataIndex: 'materialName', width: 140, ellipsis: true },
    { title: '规格', dataIndex: 'spec', width: 100, ellipsis: true },
    { title: '单位', dataIndex: 'unit', width: 60 },
    { title: '询价数量', dataIndex: 'qty', width: 90, align: 'right', render: fmtQty },
    {
      title: '定标供应商',
      key: 'awardSupplier',
      width: 120,
      render: (_, r) =>
        r.awardSupplierId != null ? <Tag color="success">{supplierName(r.awardSupplierId)}</Tag> : '-',
    },
    {
      title: '中标单价',
      dataIndex: 'awardPrice',
      width: 90,
      align: 'right',
      render: (v: number | null) => (v == null ? '-' : v.toFixed(4)),
    },
    {
      title: '中标交期(天)',
      dataIndex: 'awardLeadDays',
      width: 100,
      align: 'right',
      render: (v: number | null) => v ?? '-',
    },
    {
      title: '采购订单',
      dataIndex: 'purOrderId',
      width: 100,
      render: (v: number | null) => (v != null ? <Tag color="blue">已下推 #{v}</Tag> : '-'),
    },
  ]

  return (
    <Drawer title={`询比价详情 ${data?.rfqNo ?? ''}`} width={1180} open onClose={onClose}>
      {isLoading || !data ? (
        <Card loading style={{ border: 'none' }} />
      ) : (
        <>
          <Descriptions bordered size="small" column={3}>
            <Descriptions.Item label="询比价单号">{data.rfqNo}</Descriptions.Item>
            <Descriptions.Item label="状态"><RfqStatusTag status={data.status} /></Descriptions.Item>
            <Descriptions.Item label="截止日期">{data.deadline ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="来源请购单">{data.reqNo ?? '独立询价'}</Descriptions.Item>
            <Descriptions.Item label="定标时间">{data.awardTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{data.createTime}</Descriptions.Item>
            <Descriptions.Item label="备注" span={3}>{data.remark ?? '-'}</Descriptions.Item>
          </Descriptions>

          <Title level={5} style={{ margin: '16px 0 8px' }}>
            邀请供应商
          </Title>
          <Table<RfqSupplierRow>
            rowKey="id"
            size="small"
            columns={supplierColumns}
            dataSource={data.suppliers ?? []}
            pagination={false}
          />

          <Title level={5} style={{ margin: '16px 0 8px' }}>
            询价明细
          </Title>
          <Table<RfqItemRow>
            rowKey="id"
            size="small"
            columns={itemColumns}
            dataSource={data.items ?? []}
            pagination={false}
            scroll={{ x: 1000 }}
          />
        </>
      )}
    </Drawer>
  )
}

/** 表单值类型 */
interface RfqFormValues {
  requisitionId?: number
  deadline?: Dayjs
  supplierIds?: number[]
  remark?: string
  items?: {
    requisitionItemId?: number
    materialId?: number
    qty?: number
    remark?: string
  }[]
}

/** 可选来源请购单（已提交/已转采购） */
function useSourceRequisitions() {
  return useQuery({
    queryKey: ['purchase', 'rfq-source-requisitions'],
    queryFn: async () => {
      const page = await requisitionApi.page({ pageNum: 1, pageSize: 100 })
      return page.records.filter((r) => r.status === 10 || r.status === 20)
    },
    staleTime: 60 * 1000,
  })
}

/** 新建/修改询比价抽屉（新建可关联请购单带入明细；修改仅截止日期/供应商/备注） */
function RfqFormDrawer({
  editing,
  prefillReq,
  onClose,
  onCreated,
}: {
  editing: RfqDetail | null
  prefillReq: RequisitionDetail | null
  onClose: () => void
  onCreated?: () => void
}) {
  const [form] = Form.useForm<RfqFormValues>()
  const queryClient = useQueryClient()
  const suppliers = useSupplierOptions()
  const materials = useMaterialOptions()
  const sourceRequisitions = useSourceRequisitions()
  const [linkedReq, setLinkedReq] = useState<RequisitionDetail | null>(prefillReq)
  const watchedRequisitionId = Form.useWatch('requisitionId', form)

  /** 切换来源请购单时拉详情带入明细（只带还有剩余可转的行，数量=剩余） */
  useEffect(() => {
    if (editing || watchedRequisitionId == null) return
    let cancelled = false
    void (async () => {
      try {
        const req = await requisitionApi.get(watchedRequisitionId)
        if (!cancelled) {
          setLinkedReq(req)
          const rows = req.items
            .filter((i) => i.qty - i.orderedQty > 0)
            .map((i) => ({
              requisitionItemId: i.id,
              materialId: i.materialId,
              qty: i.qty - i.orderedQty,
            }))
          if (rows.length > 0) {
            form.setFieldValue('items', rows)
          } else {
            message.warning('该请购单已无可转余量')
          }
        }
      } catch {
        /* 静默 */
      }
    })()
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [watchedRequisitionId])

  /** 请购明细余量（数量上限校验用） */
  const remainingByItem = useMemo(() => {
    const map = new Map<number, number>()
    linkedReq?.items.forEach((i) => map.set(i.id, i.qty - i.orderedQty))
    return map
  }, [linkedReq])

  const initialValues: RfqFormValues = editing
    ? {
        requisitionId: editing.requisitionId ?? undefined,
        deadline: editing.deadline ? dayjs(editing.deadline) : undefined,
        supplierIds: editing.suppliers.map((s) => s.supplierId),
        remark: editing.remark ?? undefined,
        items: editing.items.map((i) => ({
          requisitionItemId: i.requisitionItemId ?? undefined,
          materialId: i.materialId,
          qty: i.qty,
          remark: i.remark ?? undefined,
        })),
      }
    : {
        deadline: dayjs().add(3, 'day'),
        items: prefillReq
          ? prefillReq.items
              .filter((i) => i.qty - i.orderedQty > 0)
              .map((i) => ({
                requisitionItemId: i.id,
                materialId: i.materialId,
                qty: i.qty - i.orderedQty,
              }))
          : [{}],
      }

  const saveMut = useMutation({
    mutationFn: async (payload: RfqSavePayload): Promise<void> => {
      if (editing) {
        await rfqApi.update(editing.id, payload)
      } else {
        await rfqApi.create(payload)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '创建成功（询价中）')
      queryClient.invalidateQueries({ queryKey: ['purchase', 'rfqs'] })
      queryClient.invalidateQueries({ queryKey: ['purchase', 'rfq-detail'] })
      onCreated?.()
      onClose()
    },
  })

  const onFinish = (values: RfqFormValues) => {
    const supplierIds = values.supplierIds ?? []
    if (new Set(supplierIds).size < 3) {
      message.warning('询比价至少邀请 3 家供应商')
      return
    }
    const items = (values.items ?? []).filter((d) => d.materialId != null && (d.qty ?? 0) > 0)
    if (items.length === 0) {
      message.warning('请至少录入一行有效明细（物料+数量）')
      return
    }
    saveMut.mutate({
      requisitionId: values.requisitionId,
      deadline: values.deadline!.format('YYYY-MM-DD'),
      supplierIds,
      remark: values.remark,
      items: items.map((d) => ({
        requisitionItemId: d.requisitionItemId,
        materialId: d.materialId!,
        qty: d.qty!,
        remark: d.remark,
      })),
    })
  }

  const materialOptions = (materials.data ?? []).map((m) => ({
    value: m.id,
    label: optionLabel(m),
  }))

  return (
    <Drawer
      title={editing ? `修改询比价 ${editing.rfqNo}` : '新建询比价'}
      width={1080}
      open
      onClose={onClose}
      footer={
        <Space style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={saveMut.isPending} onClick={() => form.submit()}>
            提交
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" initialValues={initialValues} onFinish={onFinish}>
        <Row gutter={16}>
          <Col span={10}>
            <Form.Item name="requisitionId" label="来源请购单（可选，带入明细）">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="选择已提交的请购单"
                disabled={!!editing}
                options={(sourceRequisitions.data ?? []).map((r) => ({
                  value: r.id,
                  label: `${r.reqNo}（${r.deptName ?? ''}）`,
                }))}
              />
            </Form.Item>
          </Col>
          <Col span={7}>
            <Form.Item name="deadline" label="报价截止日期" rules={[{ required: true, message: '请选择日期' }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={7}>
            <Form.Item
              name="supplierIds"
              label="邀请供应商（至少 3 家）"
              rules={[{ required: true, message: '请选择供应商' }]}
            >
              <Select
                mode="multiple"
                showSearch
                optionFilterProp="label"
                placeholder="选择 >=3 家供应商"
                options={(suppliers.data ?? []).map((s) => ({ value: s.id, label: optionLabel(s) }))}
              />
            </Form.Item>
          </Col>
        </Row>

        <Title level={5} style={{ margin: '4px 0 8px' }}>
          询价明细{editing && <Text type="secondary">（明细创建后不可修改）</Text>}
        </Title>
        <Form.List name="items">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => (
                <Row key={field.key} gutter={8} style={{ marginBottom: 4 }} align="middle">
                  <Col span={1} />
                  <Col span={8}>
                    <Form.Item
                      noStyle
                      shouldUpdate={(prev, cur) => prev.items !== cur.items}
                    >
                      {() => null}
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'materialId']}
                      rules={[{ required: true, message: '选择物料' }]}
                      style={{ marginBottom: 8 }}
                    >
                      <Select
                        showSearch
                        optionFilterProp="label"
                        placeholder="物料"
                        disabled={!!editing}
                        options={materialOptions}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={4}>
                    <Form.Item
                      name={[field.name, 'qty']}
                      rules={[
                        { required: true, message: '数量' },
                        {
                          validator: (_, value: number | undefined) => {
                            const row = form.getFieldValue('items')?.[field.name] as
                              | { requisitionItemId?: number }
                              | undefined
                            const remain =
                              row?.requisitionItemId != null
                                ? remainingByItem.get(row.requisitionItemId)
                                : undefined
                            if (remain != null && value != null && value > remain) {
                              return Promise.reject(new Error(`超出请购剩余可转 ${remain}`))
                            }
                            return Promise.resolve()
                          },
                        },
                      ]}
                      style={{ marginBottom: 8 }}
                    >
                      <InputNumber
                        min={0.0001}
                        placeholder="询价数量"
                        style={{ width: '100%' }}
                        disabled={!!editing}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item name={[field.name, 'remark']} style={{ marginBottom: 8 }}>
                      <Input placeholder="行备注" maxLength={200} disabled={!!editing} />
                    </Form.Item>
                  </Col>
                  <Col span={1}>
                    {!editing && (
                      <DeleteOutlined
                        onClick={() => fields.length > 1 && remove(field.name)}
                        style={{ color: fields.length > 1 ? '#ff4d4f' : '#c0c4cc', cursor: fields.length > 1 ? 'pointer' : 'not-allowed' }}
                      />
                    )}
                  </Col>
                </Row>
              ))}
              {!editing && (
                <Button
                  type="dashed"
                  block
                  icon={<PlusOutlined />}
                  onClick={() => add({})}
                  style={{ marginBottom: 12 }}
                >
                  添加明细行
                </Button>
              )}
            </>
          )}
        </Form.List>

        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={500} showCount placeholder="询比价备注" />
        </Form.Item>
      </Form>
    </Drawer>
  )
}

/** 报价录入抽屉：选供应商 -> 逐行填单价/交期（同供应商重录=覆盖） */
function QuoteDrawer({ id, onClose }: { id: number; onClose: () => void }) {
  const queryClient = useQueryClient()
  const detail = useQuery({
    queryKey: ['purchase', 'rfq-detail', id],
    queryFn: () => rfqApi.get(id),
  })
  const comparison = useQuery({
    queryKey: ['purchase', 'rfq-comparison', id],
    queryFn: () => rfqApi.comparison(id),
  })
  const [supplierId, setSupplierId] = useState<number | null>(null)

  /** 该供应商已有报价（回显）：itemId -> {price, leadDays} */
  const existing = useMemo(() => {
    const map = new Map<number, { price: number; leadDays: number }>()
    if (supplierId != null) {
      comparison.data?.items.forEach((item) => {
        const q = item.quotes.find((c) => c.supplierId === supplierId)
        if (q) map.set(item.itemId, { price: q.price, leadDays: q.leadDays })
      })
    }
    return map
  }, [comparison.data, supplierId])

  const [rows, setRows] = useState<{ itemId: number; price?: number; leadDays?: number }[]>([])

  /** 切换供应商时重置行（回显已有报价或空行） */
  useEffect(() => {
    if (supplierId == null || !comparison.data) return
    setRows(
      comparison.data.items.map((item) => ({
        itemId: item.itemId,
        price: existing.get(item.itemId)?.price,
        leadDays: existing.get(item.itemId)?.leadDays,
      })),
    )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [supplierId, comparison.data])

  const saveMut = useMutation({
    mutationFn: () =>
      rfqApi.saveQuotes(id, {
        supplierId: supplierId!,
        quotes: rows.map((r) => ({ itemId: r.itemId, price: r.price ?? 0, leadDays: r.leadDays ?? 0 })),
      }),
    onSuccess: () => {
      message.success('报价已保存')
      queryClient.invalidateQueries({ queryKey: ['purchase', 'rfqs'] })
      queryClient.invalidateQueries({ queryKey: ['purchase', 'rfq-detail', id] })
      queryClient.invalidateQueries({ queryKey: ['purchase', 'rfq-comparison', id] })
      onClose()
    },
  })

  const items = comparison.data?.items ?? []

  const columns: ColumnsType<{ itemId: number; price?: number; leadDays?: number }> = [
    {
      title: '物料',
      key: 'material',
      render: (_, r) => {
        const item = items.find((i) => i.itemId === r.itemId)
        return `${item?.materialCode ?? ''} ${item?.materialName ?? ''}`
      },
    },
    {
      title: '询价数量',
      key: 'qty',
      width: 110,
      align: 'right',
      render: (_, r) => fmtQty(items.find((i) => i.itemId === r.itemId)?.qty),
    },
    {
      title: '报价单价',
      key: 'price',
      width: 150,
      render: (_, r) => (
        <InputNumber
          min={0}
          precision={4}
          value={r.price}
          onChange={(v) =>
            setRows((prev) => prev.map((x) => (x.itemId === r.itemId ? { ...x, price: v ?? undefined } : x)))
          }
        />
      ),
    },
    {
      title: '交期（天）',
      key: 'leadDays',
      width: 150,
      render: (_, r) => (
        <InputNumber
          min={0}
          precision={0}
          value={r.leadDays}
          onChange={(v) =>
            setRows((prev) => prev.map((x) => (x.itemId === r.itemId ? { ...x, leadDays: v ?? undefined } : x)))
          }
        />
      ),
    },
  ]

  return (
    <Drawer title={`录入报价 ${detail.data?.rfqNo ?? ''}`} width={760} open onClose={onClose}
      footer={
        <Space style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            disabled={supplierId == null}
            loading={saveMut.isPending}
            onClick={() => saveMut.mutate()}
          >
            保存报价
          </Button>
        </Space>
      }
    >
      <Form layout="vertical">
        <Form.Item label="报价供应商" required>
          <Select
            style={{ width: 300 }}
            placeholder="选择被邀请供应商"
            value={supplierId}
            onChange={setSupplierId}
            options={(detail.data?.suppliers ?? []).map((s) => ({
              value: s.supplierId,
              label: `${s.supplierName ?? ''}${s.status === 10 ? '（已报价）' : ''}`,
            }))}
          />
        </Form.Item>
      </Form>
      {supplierId != null && (
        <Table
          rowKey="itemId"
          size="small"
          columns={columns}
          dataSource={rows}
          pagination={false}
          loading={comparison.isLoading}
        />
      )}
    </Drawer>
  )
}

/** 比价矩阵抽屉：物料×供应商（单价/交期/总价），最低价高亮 */
function ComparisonDrawer({ id, onClose }: { id: number; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['purchase', 'rfq-comparison', id],
    queryFn: () => rfqApi.comparison(id),
  })

  const columns: ColumnsType<RfqComparison['items'][number]> = useMemo(() => {
    const base: ColumnsType<RfqComparison['items'][number]> = [
      { title: '物料编码', dataIndex: 'materialCode', width: 110, fixed: 'left' },
      { title: '物料名称', dataIndex: 'materialName', width: 140, ellipsis: true },
      { title: '规格', dataIndex: 'spec', width: 100, ellipsis: true },
      { title: '单位', dataIndex: 'unit', width: 60 },
      { title: '数量', dataIndex: 'qty', width: 90, align: 'right', render: fmtQty },
    ]
    const supplierCols: ColumnsType<RfqComparison['items'][number]> = (data?.suppliers ?? []).map((s) => ({
      title: s.supplierName ?? String(s.supplierId),
      key: `s-${s.supplierId}`,
      width: 190,
      render: (_, row) => {
        const q = row.quotes.find((c) => c.supplierId === s.supplierId)
        if (!q) return <Text type="secondary">未报价</Text>
        return (
          <div style={{ lineHeight: 1.5 }}>
            <Space size={4}>
              <Text strong={q.lowest} style={q.lowest ? { color: '#389e0d' } : undefined}>
                单价 {q.price.toFixed(4)}
              </Text>
              {q.lowest && <Tag color="green">最低</Tag>}
            </Space>
            <br />
            <Text type="secondary" style={{ fontSize: 12 }}>
              交期 {q.leadDays} 天 · 总价 {fmtAmount(q.total)}
            </Text>
          </div>
        )
      },
    }))
    const awardCol: ColumnsType<RfqComparison['items'][number]> = [
      {
        title: '定标供应商',
        key: 'award',
        width: 120,
        render: (_, row) => {
          if (row.awardSupplierId == null) return '-'
          const s = (data?.suppliers ?? []).find((x) => x.supplierId === row.awardSupplierId)
          return <Tag color="success">{s?.supplierName ?? row.awardSupplierId}</Tag>
        },
      },
    ]
    return [...base, ...supplierCols, ...awardCol]
  }, [data])

  return (
    <Drawer title={`比价矩阵 ${data?.rfqNo ?? ''}`} width={1200} open onClose={onClose}>
      {isLoading || !data ? (
        <Card loading style={{ border: 'none' }} />
      ) : (
        <>
          <Descriptions bordered size="small" column={3} style={{ marginBottom: 12 }}>
            <Descriptions.Item label="询比价单号">{data.rfqNo}</Descriptions.Item>
            <Descriptions.Item label="状态"><RfqStatusTag status={data.status} /></Descriptions.Item>
            <Descriptions.Item label="截止日期">{data.deadline ?? '-'}</Descriptions.Item>
          </Descriptions>
          <Table
            rowKey="itemId"
            size="small"
            columns={columns}
            dataSource={data.items}
            pagination={false}
            scroll={{ x: 1200 }}
          />
          <Title level={5} style={{ margin: '16px 0 8px' }}>
            供应商报价汇总
          </Title>
          <Table
            rowKey="supplierId"
            size="small"
            pagination={false}
            dataSource={data.suppliers}
            columns={[
              { title: '供应商', dataIndex: 'supplierName', ellipsis: true },
              { title: '状态', dataIndex: 'status', width: 90, render: (v: number) => <RfqSupplierStatusTag status={v} /> },
              { title: '报价金额合计', dataIndex: 'quoteAmount', width: 140, align: 'right', render: fmtAmount },
            ]}
          />
        </>
      )}
    </Drawer>
  )
}

/** 分项定标弹窗：每明细选一个已报价供应商（默认最低价），允许两行选不同供应商 */
function AwardModal({ id, onClose }: { id: number; onClose: () => void }) {
  const queryClient = useQueryClient()
  const comparison = useQuery({
    queryKey: ['purchase', 'rfq-comparison', id],
    queryFn: () => rfqApi.comparison(id),
  })
  const [selected, setSelected] = useState<Record<number, number>>({})

  const items = comparison.data?.items ?? []

  /** 打开时默认选每行最低价供应商 */
  useEffect(() => {
    if (!comparison.data) return
    const init: Record<number, number> = {}
    comparison.data.items.forEach((item) => {
      const lowest = item.quotes.find((q) => q.lowest)
      if (lowest) init[item.itemId] = lowest.supplierId
    })
    setSelected(init)
  }, [comparison.data])

  const saveMut = useMutation({
    mutationFn: () => {
      const payload: RfqAwardPayload = {
        perItem: items
          .filter((i) => selected[i.itemId] != null)
          .map((i) => ({ itemId: i.itemId, supplierId: selected[i.itemId] })),
      }
      return rfqApi.award(id, payload)
    },
    onSuccess: () => {
      message.success('定标完成，请购已转数量已回写')
      queryClient.invalidateQueries({ queryKey: ['purchase', 'rfqs'] })
      queryClient.invalidateQueries({ queryKey: ['purchase', 'rfq-detail', id] })
      queryClient.invalidateQueries({ queryKey: ['purchase', 'rfq-comparison', id] })
      queryClient.invalidateQueries({ queryKey: ['purchase', 'requisitions'] })
      onClose()
    },
  })

  const columns: ColumnsType<RfqComparison['items'][number]> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 110 },
    { title: '物料名称', dataIndex: 'materialName', width: 150, ellipsis: true },
    { title: '规格', dataIndex: 'spec', width: 110, ellipsis: true },
    { title: '数量', dataIndex: 'qty', width: 90, align: 'right', render: fmtQty },
    {
      title: '报价情况（绿=最低）',
      key: 'quotes',
      render: (_, row) => (
        <Space wrap size={4}>
          {row.quotes.map((q) => {
            const s = comparison.data?.suppliers.find((x) => x.supplierId === q.supplierId)
            return (
              <Tag key={q.supplierId} color={q.lowest ? 'green' : 'default'}>
                {s?.supplierName ?? q.supplierId}：{q.price.toFixed(4)} / {q.leadDays}天
              </Tag>
            )
          })}
        </Space>
      ),
    },
    {
      title: '中标供应商',
      key: 'select',
      width: 220,
      render: (_, row) => (
        <Select
          style={{ width: '100%' }}
          placeholder="选择供应商"
          value={selected[row.itemId]}
          onChange={(v) => setSelected((prev) => ({ ...prev, [row.itemId]: v }))}
          options={row.quotes.map((q) => ({
            value: q.supplierId,
            label: `${
              comparison.data?.suppliers.find((x) => x.supplierId === q.supplierId)?.supplierName ?? q.supplierId
            }（${q.price.toFixed(4)}）`,
          }))}
        />
      ),
    },
  ]

  const allChosen = items.length > 0 && items.every((i) => selected[i.itemId] != null)

  return (
    <Modal
      title={`分项定标 ${comparison.data?.rfqNo ?? ''}`}
      width={1100}
      open
      onCancel={onClose}
      onOk={() => saveMut.mutate()}
      okText="确认定标"
      okButtonProps={{ disabled: !allChosen, loading: saveMut.isPending }}
      cancelText="取消"
    >
      <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
        每行明细选择一个已报价供应商（默认最低价），允许分项定标给不同供应商；定标后回写请购已转数量，单据锁定。
      </Text>
      <Table
        rowKey="itemId"
        size="small"
        columns={columns}
        dataSource={items}
        pagination={false}
        loading={comparison.isLoading}
        scroll={{ x: 1000 }}
      />
    </Modal>
  )
}
