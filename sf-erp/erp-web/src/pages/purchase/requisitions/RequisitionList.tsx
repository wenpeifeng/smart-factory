import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
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
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import {
  requisitionApi,
  type RequisitionDetail,
  type RequisitionItemRow,
  type RequisitionRow,
  type RequisitionSavePayload,
} from '@/api/purchase'
import { fmtQty, optionLabel, useMaterialOptions } from '../shared'

const { Title, Text } = Typography

/** 请购单状态 -> 文案/颜色（与后端 pur_requisition.status 注释一致） */
const REQ_STATUS_MAP: Record<number, { text: string; color: string }> = {
  0: { text: '草稿', color: 'default' },
  10: { text: '已提交', color: 'processing' },
  20: { text: '已转采购', color: 'success' },
  30: { text: '已关闭', color: 'error' },
}

const REQ_STATUS_OPTIONS = Object.entries(REQ_STATUS_MAP).map(([value, cfg]) => ({
  value: Number(value),
  label: cfg.text,
}))

function ReqStatusTag({ status }: { status: number }) {
  const cfg = REQ_STATUS_MAP[status] ?? { text: `未知(${status})`, color: 'default' }
  return <Tag color={cfg.color}>{cfg.text}</Tag>
}

/** 列表筛选状态 */
interface FilterState {
  keyword: string
  status?: number
  deptId?: number
  materialId?: number
}

/**
 * 请购单页（请购 -> 询比价 -> 采购订单链路源头）：
 * 列表（状态/部门/物料筛选）+ Drawer 详情（明细含已转数量/剩余）+
 * Drawer 新建/修改（Form.List 明细行）+ 提交/关闭 + 转询价跳转。
 */
export default function RequisitionList() {
  const [filter, setFilter] = useState<FilterState>({ keyword: '' })
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [formState, setFormState] = useState<{ open: boolean; editing: RequisitionDetail | null }>({
    open: false,
    editing: null,
  })
  const navigate = useNavigate()

  const materials = useMaterialOptions()
  const depts = useQuery({
    queryKey: ['purchase', 'requisition-dept-options'],
    queryFn: () => requisitionApi.deptOptions(),
    staleTime: 5 * 60 * 1000,
  })

  const params = {
    pageNum,
    pageSize,
    keyword: filter.keyword || undefined,
    status: filter.status,
    deptId: filter.deptId,
    materialId: filter.materialId,
  }

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['purchase', 'requisitions', params],
    queryFn: () => requisitionApi.page(params),
  })

  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['purchase', 'requisitions'] })

  const submitMut = useMutation({
    mutationFn: requisitionApi.submit,
    onSuccess: () => {
      message.success('已提交')
      invalidate()
    },
  })
  const closeMut = useMutation({
    mutationFn: requisitionApi.close,
    onSuccess: () => {
      message.success('已关闭')
      invalidate()
    },
  })
  const deleteMut = useMutation({
    mutationFn: requisitionApi.remove,
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
  })

  /** 打开修改：先取详情再开表单（保证主子表一次性完整） */
  const openEdit = async (id: number) => {
    try {
      const detail = await requisitionApi.get(id)
      setFormState({ open: true, editing: detail })
    } catch {
      /* 错误提示由 request 拦截器统一处理 */
    }
  }

  const columns: ColumnsType<RequisitionRow> = [
    { title: '请购单号', dataIndex: 'reqNo', width: 170 },
    { title: '请购部门', dataIndex: 'deptName', width: 130, ellipsis: true },
    { title: '请购人', dataIndex: 'applicantName', width: 100, ellipsis: true },
    { title: '期望日期', dataIndex: 'expectDate', width: 110 },
    { title: '状态', dataIndex: 'status', width: 100, render: (v: number) => <ReqStatusTag status={v} /> },
    { title: '创建时间', dataIndex: 'createTime', width: 165, render: (v: string) => v ?? '-' },
    {
      title: '操作',
      key: 'actions',
      width: 300,
      fixed: 'right',
      render: (_, row) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => setDetailId(row.id)}>
            详情
          </Button>
          {row.status === 0 && (
            <HasPerm code="purchase:requisition:edit">
              <Button type="link" size="small" onClick={() => void openEdit(row.id)}>
                修改
              </Button>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="purchase:requisition:audit">
              <Popconfirm title="确认提交该请购单？" onConfirm={() => submitMut.mutate(row.id)}>
                <Button type="link" size="small">
                  提交
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {(row.status === 10 || row.status === 20) && (
            <HasPerm code="purchase:rfq:add">
              <Button
                type="link"
                size="small"
                onClick={() => navigate(`/purchase/rfqs?from=${row.id}`)}
              >
                生成询价
              </Button>
            </HasPerm>
          )}
          {(row.status === 10 || row.status === 20) && (
            <HasPerm code="purchase:requisition:close">
              <Popconfirm title="关闭后余量不再采购，确认关闭？" onConfirm={() => closeMut.mutate(row.id)}>
                <Button type="link" size="small" danger>
                  关闭
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 0 && (
            <HasPerm code="purchase:requisition:delete">
              <Popconfirm title="确认删除该草稿请购单？" onConfirm={() => deleteMut.mutate(row.id)}>
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
        请购管理
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="请购单号模糊搜索"
            style={{ width: 220 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setFilter((f) => ({ ...f, keyword: v }))
            }}
          />
          <Select
            placeholder="请购部门"
            style={{ width: 180 }}
            allowClear
            showSearch
            optionFilterProp="label"
            options={(depts.data ?? []).map((d) => ({ value: d.id, label: d.name }))}
            onChange={(v) => {
              setPageNum(1)
              setFilter((f) => ({ ...f, deptId: v }))
            }}
          />
          <Select
            placeholder="物料"
            style={{ width: 220 }}
            allowClear
            showSearch
            optionFilterProp="label"
            options={(materials.data ?? []).map((m) => ({ value: m.id, label: optionLabel(m) }))}
            onChange={(v) => {
              setPageNum(1)
              setFilter((f) => ({ ...f, materialId: v }))
            }}
          />
          <Select
            placeholder="状态"
            style={{ width: 130 }}
            allowClear
            options={REQ_STATUS_OPTIONS}
            onChange={(v) => {
              setPageNum(1)
              setFilter((f) => ({ ...f, status: v }))
            }}
          />
          <HasPerm code="purchase:requisition:add">
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setFormState({ open: true, editing: null })}
            >
              新建请购单
            </Button>
          </HasPerm>
        </Space>

        <Table<RequisitionRow>
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

      {detailId != null && <ReqDetailDrawer id={detailId} onClose={() => setDetailId(null)} />}

      {formState.open && (
        <ReqFormDrawer
          key={formState.editing?.id ?? 'new'}
          editing={formState.editing}
          onClose={() => setFormState({ open: false, editing: null })}
        />
      )}
    </div>
  )
}

/** 请购明细表格列（详情抽屉复用；展示已转/剩余守卫口径） */
const DETAIL_COLUMNS: ColumnsType<RequisitionItemRow> = [
  { title: '物料编码', dataIndex: 'materialCode', width: 120 },
  { title: '物料名称', dataIndex: 'materialName', width: 150, ellipsis: true },
  { title: '规格', dataIndex: 'spec', width: 110, ellipsis: true },
  { title: '单位', dataIndex: 'unit', width: 70 },
  { title: '请购数量', dataIndex: 'qty', width: 100, align: 'right', render: fmtQty },
  { title: '已转采购', dataIndex: 'orderedQty', width: 100, align: 'right', render: fmtQty },
  {
    title: '剩余可转',
    key: 'remain',
    width: 100,
    align: 'right',
    render: (_, r) => <Text type={r.qty - r.orderedQty > 0 ? undefined : 'secondary'}>{fmtQty(r.qty - r.orderedQty)}</Text>,
  },
  { title: '需求日期', dataIndex: 'requireDate', width: 110, render: (v: string | null) => v ?? '-' },
  { title: '备注', dataIndex: 'remark', ellipsis: true },
]

/** 请购单详情抽屉 */
function ReqDetailDrawer({ id, onClose }: { id: number; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['purchase', 'requisition-detail', id],
    queryFn: () => requisitionApi.get(id),
  })

  return (
    <Drawer title={`请购单详情 ${data?.reqNo ?? ''}`} width={1000} open onClose={onClose}>
      {isLoading || !data ? (
        <Card loading style={{ border: 'none' }} />
      ) : (
        <>
          <Descriptions bordered size="small" column={3}>
            <Descriptions.Item label="请购单号">{data.reqNo}</Descriptions.Item>
            <Descriptions.Item label="状态"><ReqStatusTag status={data.status} /></Descriptions.Item>
            <Descriptions.Item label="请购部门">{data.deptName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="请购人">{data.applicantName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="期望日期">{data.expectDate}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{data.createTime}</Descriptions.Item>
            <Descriptions.Item label="备注" span={3}>{data.remark ?? '-'}</Descriptions.Item>
          </Descriptions>

          <Title level={5} style={{ margin: '16px 0 8px' }}>
            请购明细
          </Title>
          <Table<RequisitionItemRow>
            rowKey="id"
            size="small"
            columns={DETAIL_COLUMNS}
            dataSource={data.items ?? []}
            pagination={false}
            scroll={{ x: 900 }}
          />
        </>
      )}
    </Drawer>
  )
}

/** 表单值类型（DatePicker 存 Dayjs，提交时转换） */
interface ReqFormValues {
  deptId?: number
  expectDate?: Dayjs
  remark?: string
  items?: {
    materialId?: number
    qty?: number
    requireDate?: Dayjs | null
    remark?: string
  }[]
}

/** 新建/修改请购单抽屉表单 */
function ReqFormDrawer({ editing, onClose }: { editing: RequisitionDetail | null; onClose: () => void }) {
  const [form] = Form.useForm<ReqFormValues>()
  const queryClient = useQueryClient()
  const materials = useMaterialOptions()
  const depts = useQuery({
    queryKey: ['purchase', 'requisition-dept-options'],
    queryFn: () => requisitionApi.deptOptions(),
    staleTime: 5 * 60 * 1000,
  })

  const materialOptions = (materials.data ?? []).map((m) => ({
    value: m.id,
    label: optionLabel(m),
  }))

  const initialValues: ReqFormValues = editing
    ? {
        deptId: editing.deptId,
        expectDate: dayjs(editing.expectDate),
        remark: editing.remark ?? undefined,
        items: editing.items.map((d) => ({
          materialId: d.materialId,
          qty: d.qty,
          requireDate: d.requireDate ? dayjs(d.requireDate) : undefined,
          remark: d.remark ?? undefined,
        })),
      }
    : {
        expectDate: dayjs().add(7, 'day'),
        items: [{}],
      }

  const saveMut = useMutation({
    mutationFn: async (payload: RequisitionSavePayload): Promise<void> => {
      if (editing) {
        await requisitionApi.update(editing.id, payload)
      } else {
        await requisitionApi.create(payload)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '创建成功（草稿）')
      queryClient.invalidateQueries({ queryKey: ['purchase', 'requisitions'] })
      queryClient.invalidateQueries({ queryKey: ['purchase', 'requisition-detail'] })
      onClose()
    },
  })

  const onFinish = (values: ReqFormValues) => {
    const items = (values.items ?? []).filter((d) => d.materialId != null && (d.qty ?? 0) > 0)
    if (items.length === 0) {
      message.warning('请至少录入一行有效明细（物料+数量）')
      return
    }
    saveMut.mutate({
      deptId: values.deptId!,
      expectDate: values.expectDate!.format('YYYY-MM-DD'),
      remark: values.remark,
      items: items.map((d) => ({
        materialId: d.materialId!,
        qty: d.qty!,
        requireDate: d.requireDate ? d.requireDate.format('YYYY-MM-DD') : undefined,
        remark: d.remark,
      })),
    })
  }

  return (
    <Drawer
      title={editing ? `修改请购单 ${editing.reqNo}` : '新建请购单'}
      width={980}
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
          <Col span={8}>
            <Form.Item name="deptId" label="请购部门" rules={[{ required: true, message: '请选择部门' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="选择部门"
                options={(depts.data ?? []).map((d) => ({ value: d.id, label: d.name }))}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="expectDate" label="期望到货日期" rules={[{ required: true, message: '请选择日期' }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item label="请购人">
              <Input disabled placeholder="默认当前登录用户" />
            </Form.Item>
          </Col>
        </Row>

        <Title level={5} style={{ margin: '4px 0 8px' }}>
          请购明细
        </Title>
        <Form.List name="items">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => (
                <Row key={field.key} gutter={8} style={{ marginBottom: 4 }} align="middle">
                  <Col span={9}>
                    <Form.Item
                      name={[field.name, 'materialId']}
                      rules={[{ required: true, message: '选择物料' }]}
                      style={{ marginBottom: 8 }}
                    >
                      <Select showSearch optionFilterProp="label" placeholder="物料" options={materialOptions} />
                    </Form.Item>
                  </Col>
                  <Col span={4}>
                    <Form.Item
                      name={[field.name, 'qty']}
                      rules={[{ required: true, message: '数量' }]}
                      style={{ marginBottom: 8 }}
                    >
                      <InputNumber min={0.0001} placeholder="请购数量" style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item name={[field.name, 'requireDate']} style={{ marginBottom: 8 }}>
                      <DatePicker placeholder="需求日期" style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                  <Col span={4}>
                    <Form.Item name={[field.name, 'remark']} style={{ marginBottom: 8 }}>
                      <Input placeholder="备注" maxLength={200} />
                    </Form.Item>
                  </Col>
                  <Col span={1}>
                    <DeleteOutlined
                      onClick={() => fields.length > 1 && remove(field.name)}
                      style={{ color: fields.length > 1 ? '#ff4d4f' : '#c0c4cc', cursor: fields.length > 1 ? 'pointer' : 'not-allowed' }}
                    />
                  </Col>
                </Row>
              ))}
              <Button
                type="dashed"
                block
                icon={<PlusOutlined />}
                onClick={() => add({})}
                style={{ marginBottom: 12 }}
              >
                添加明细行
              </Button>
            </>
          )}
        </Form.List>

        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={500} showCount placeholder="请购备注" />
        </Form.Item>
      </Form>
    </Drawer>
  )
}
