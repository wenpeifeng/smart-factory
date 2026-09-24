import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import {
  BIZ_TYPE_OPTIONS,
  fetchFlowPage,
  fetchMaterialOptions,
  fetchWarehouseOptions,
  fmtAmount,
  fmtPeriod,
  fmtPrice,
  fmtQty,
  receiveHook,
  syncCost,
  type CostFlowRow,
} from '@/api/cost'

const { Title } = Typography

/** 计价流水查询表单值 */
interface FilterForm {
  period: Dayjs | null
  materialId?: number
  bizType?: string
}

/** 重算表单值 */
interface SyncForm {
  from: Dayjs | null
  to: Dayjs | null
}

/** 补录表单值 */
interface HookForm {
  materialId: number
  warehouseId?: number
  bizType: string
  bizNo: string
  qty: number
  amount?: number
  bizDate?: Dayjs | null
}

/**
 * 存货计价页（移动加权平均流水台账）。
 * 筛选：期间 / 物料 / 单据类型；操作：期间重算（sync）、单笔补录（hook）。
 */
export default function CostFlowPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [filters, setFilters] = useState<FilterForm>({ period: dayjs() })
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [syncOpen, setSyncOpen] = useState(false)
  const [hookOpen, setHookOpen] = useState(false)
  const [filterForm] = Form.useForm<FilterForm>()
  const [syncForm] = Form.useForm<SyncForm>()
  const [hookForm] = Form.useForm<HookForm>()

  const { data, isLoading, isFetching } = useQuery({
    queryKey: [
      'cost',
      'flows',
      filters.period?.format('YYYYMM') ?? '',
      filters.materialId ?? 0,
      filters.bizType ?? '',
      pageNum,
      pageSize,
    ],
    queryFn: () =>
      fetchFlowPage({
        pageNum,
        pageSize,
        period: filters.period ? filters.period.format('YYYYMM') : undefined,
        materialId: filters.materialId,
        bizType: filters.bizType,
      }),
  })
  const { data: materials } = useQuery({
    queryKey: ['cost', 'material-options'],
    queryFn: () => fetchMaterialOptions(),
  })
  const { data: warehouses } = useQuery({
    queryKey: ['cost', 'warehouse-options'],
    queryFn: fetchWarehouseOptions,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['cost'] })

  const syncMutation = useMutation({
    mutationFn: (values: SyncForm) =>
      syncCost(values.from ? values.from.format('YYYYMM') : undefined, values.to!.format('YYYYMM')),
    onSuccess: (r) => {
      message.success(
        `重算完成：${fmtPeriod(r.fromPeriod)}~${fmtPeriod(r.toPeriod)}，重放流水 ${r.movementsProcessed} 条，生成计价流水 ${r.flowsInserted} 条`,
      )
      setSyncOpen(false)
      invalidate()
    },
  })

  const hookMutation = useMutation({
    mutationFn: (values: HookForm) =>
      receiveHook({
        materialId: values.materialId,
        warehouseId: values.warehouseId,
        bizType: values.bizType,
        bizNo: values.bizNo,
        qty: values.qty,
        amount: values.amount ?? undefined,
        bizDate: values.bizDate ? values.bizDate.format('YYYY-MM-DD') : undefined,
      }),
    onSuccess: (r) => {
      message.success(
        `补录成功：数量 ${fmtQty(r.qty)} × 单价 ${fmtPrice(r.unitCost)} = 金额 ${fmtAmount(r.amount)}，结存 ${fmtQty(r.balanceQty)} / ${fmtAmount(r.balanceCost)}`,
      )
      setHookOpen(false)
      invalidate()
    },
  })

  const columns: ColumnsType<CostFlowRow> = [
    { title: '期间', dataIndex: 'period', width: 90, render: fmtPeriod },
    {
      title: '业务类型',
      dataIndex: 'bizType',
      width: 100,
      render: (_, r) => <Tag color={r.qty >= 0 ? 'blue' : 'volcano'}>{r.bizTypeLabel}</Tag>,
    },
    { title: '来源单号', dataIndex: 'bizNo', width: 150, render: (v) => v ?? '-' },
    {
      title: '物料',
      key: 'material',
      ellipsis: true,
      render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}`.trim() || '-',
    },
    { title: '仓库', dataIndex: 'warehouseName', width: 110, render: (v) => v ?? '-' },
    { title: '业务日期', dataIndex: 'bizDate', width: 110, render: (v) => v ?? '-' },
    {
      title: '数量',
      dataIndex: 'qty',
      align: 'right',
      width: 100,
      render: (v: number) => (
        <span style={{ color: v < 0 ? '#cf1322' : undefined }}>{fmtQty(v)}</span>
      ),
    },
    { title: '单价', dataIndex: 'unitCost', align: 'right', width: 110, render: fmtPrice },
    {
      title: '金额',
      dataIndex: 'amount',
      align: 'right',
      width: 120,
      render: (v: number) => (
        <span style={{ color: v < 0 ? '#cf1322' : undefined }}>{fmtAmount(v)}</span>
      ),
    },
    { title: '结存数量', dataIndex: 'balanceQty', align: 'right', width: 100, render: fmtQty },
    { title: '结存金额', dataIndex: 'balanceCost', align: 'right', width: 120, render: fmtAmount },
  ]

  const applyFilters = (values: FilterForm) => {
    setPageNum(1)
    setFilters(values)
  }

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        存货计价
      </Title>

      <Card>
        <Form
          form={filterForm}
          layout="inline"
          initialValues={{ period: dayjs() }}
          onFinish={applyFilters}
          style={{ marginBottom: 16 }}
        >
          <Form.Item name="period" label="期间">
            <DatePicker picker="month" allowClear placeholder="全部期间" />
          </Form.Item>
          <Form.Item name="materialId" label="物料">
            <Select
              showSearch
              allowClear
              placeholder="全部物料"
              style={{ width: 220 }}
              optionFilterProp="label"
              options={(materials ?? []).map((m) => ({
                label: `${m.code} ${m.name}`,
                value: m.id,
              }))}
            />
          </Form.Item>
          <Form.Item name="bizType" label="单据类型">
            <Select
              allowClear
              placeholder="全部类型"
              style={{ width: 140 }}
              options={BIZ_TYPE_OPTIONS}
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
              <Button onClick={() => filterForm.resetFields()}>重置</Button>
            </Space>
          </Form.Item>
        </Form>

        <Space style={{ marginBottom: 12 }}>
          <Button
            onClick={() => {
              syncForm.setFieldsValue({ from: dayjs().startOf('year'), to: dayjs() })
              setSyncOpen(true)
            }}
          >
            期间重算
          </Button>
          <Button
            onClick={() => {
              hookForm.resetFields()
              hookForm.setFieldsValue({ bizDate: dayjs() })
              setHookOpen(true)
            }}
          >
            单笔补录
          </Button>
        </Space>

        <Table<CostFlowRow>
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          size="small"
          scroll={{ x: 1200 }}
          pagination={{
            total: data?.total ?? 0,
            current: data?.pageNum ?? pageNum,
            pageSize: data?.pageSize ?? pageSize,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPageNum(p)
              setPageSize(s)
            },
          }}
        />
      </Card>

      {/* 期间重算弹窗 */}
      <Modal
        title="按库存流水重算计价"
        open={syncOpen}
        onCancel={() => setSyncOpen(false)}
        onOk={() => syncForm.submit()}
        confirmLoading={syncMutation.isPending}
        destroyOnClose
      >
        <Descriptions size="small" column={1} style={{ marginBottom: 16 }}>
          <Descriptions.Item>
            从 inv_movement 重放指定期间的流水重建计价台账（初始化/补账用）。已结账期间受保护，
            重算起点自动推到其后的第一个月。PUR_IN 金额取采购入库单明细，其余按移动加权价推算。
          </Descriptions.Item>
        </Descriptions>
        <Form form={syncForm} layout="vertical" onFinish={(v) => syncMutation.mutate(v)}>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="from"
              label="起始期间（可空）"
              style={{ width: 200 }}
              extra="留空表示从最早流水起算"
            >
              <DatePicker picker="month" allowClear />
            </Form.Item>
            <Form.Item
              name="to"
              label="结束期间"
              rules={[{ required: true, message: '请选择结束期间' }]}
              style={{ width: 200 }}
            >
              <DatePicker picker="month" />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* 单笔补录弹窗 */}
      <Modal
        title="补录业务计价"
        open={hookOpen}
        onCancel={() => setHookOpen(false)}
        onOk={() => hookForm.submit()}
        confirmLoading={hookMutation.isPending}
        destroyOnClose
        width={560}
      >
        <Form form={hookForm} layout="vertical" onFinish={(v) => hookMutation.mutate(v)}>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="materialId"
              label="物料"
              rules={[{ required: true, message: '请选择物料' }]}
              style={{ width: 260 }}
            >
              <Select
                showSearch
                placeholder="选择物料"
                optionFilterProp="label"
                options={(materials ?? []).map((m) => ({
                  label: `${m.code} ${m.name}`,
                  value: m.id,
                }))}
              />
            </Form.Item>
            <Form.Item name="warehouseId" label="仓库（可选）" style={{ width: 240 }}>
              <Select
                allowClear
                placeholder="仅溯源"
                options={(warehouses ?? []).map((w) => ({ label: w.name, value: w.id }))}
              />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="bizType"
              label="业务类型"
              rules={[{ required: true, message: '请选择业务类型' }]}
              style={{ width: 260 }}
              extra="入库类型可填金额；出库类型按移动加权价计算"
            >
              <Select placeholder="选择类型" options={BIZ_TYPE_OPTIONS} />
            </Form.Item>
            <Form.Item
              name="bizNo"
              label="来源单号"
              rules={[{ required: true, message: '请输入来源单号' }]}
              style={{ width: 240 }}
            >
              <Input placeholder="如 PO202609010001" maxLength={40} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="qty"
              label="数量（正数）"
              rules={[{ required: true, message: '请输入数量' }]}
              style={{ width: 150 }}
            >
              <InputNumber min={0.0001} style={{ width: '100%' }} placeholder=">0" />
            </Form.Item>
            <Form.Item name="amount" label="入库金额（可选）" style={{ width: 180 }}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder="按加权价估算" />
            </Form.Item>
            <Form.Item name="bizDate" label="业务日期" style={{ width: 170 }}>
              <DatePicker />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  )
}
