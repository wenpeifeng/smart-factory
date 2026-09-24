import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { getEnabledCustomers } from '@/api/basedata'
import {
  applySalesFromOrder,
  getTaxStat,
  issueSalesInvoice,
  listToBeInvoiced,
  pageSalesInvoices,
  reverseSalesInvoice,
  type InvoiceStatus,
  type SalesInvoiceVO,
  type ToBeInvoicedVO,
} from '@/api/invoice'

const { Title } = Typography

/** 销项发票状态 → 标签配色与文案 */
const STATUS_TAG: Record<InvoiceStatus, { color: string; text: string }> = {
  0: { color: 'orange', text: '待开票' },
  10: { color: 'blue', text: '已开票' },
  20: { color: 'default', text: '已红冲' },
}

/** 金额展示（右对齐 + 千分位），负数（红字票）标红 */
const money = (v: number) =>
  v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** 税率展示（13 → 13%） */
const rate = (v: number) => `${v}%`

/** 税率可选项（增值税电子发票场景：13 / 9 / 6） */
const TAX_RATE_OPTIONS = [
  { value: 13, label: '13%' },
  { value: 9, label: '9%' },
  { value: 6, label: '6%' },
]

/** 开票申请弹窗表单值 */
interface ApplyFormValues {
  customerId?: number
  taxRate: number
}

/**
 * 销项发票页：
 * 顶部期间税负指标卡（本年）+ 分页列表（客户/状态/单号筛选）
 * + 从出库开票弹窗（客户待开票清单选单）+ 开票 / 红冲操作。
 */
export default function SalesInvoiceList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [customerId, setCustomerId] = useState<number | undefined>()
  const [status, setStatus] = useState<InvoiceStatus | undefined>()

  // 开票申请弹窗
  const [applyOpen, setApplyOpen] = useState(false)
  const [applyCustomer, setApplyCustomer] = useState<number | undefined>()
  const [selectedOutstock, setSelectedOutstock] = useState<number | undefined>()
  const [form] = Form.useForm<ApplyFormValues>()

  // 本年期间税负（与列表同屏展示，开票/红冲后联动刷新）
  const statFrom = dayjs().startOf('year').format('YYYY-MM-DD')
  const statTo = dayjs().endOf('year').format('YYYY-MM-DD')

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['invoice', 'sales', { pageNum, pageSize, keyword, customerId, status }],
    queryFn: () =>
      pageSalesInvoices({
        pageNum,
        pageSize,
        keyword: keyword || undefined,
        customerId,
        status,
      }),
  })
  const { data: taxStat } = useQuery({
    queryKey: ['invoice', 'taxStat', statFrom, statTo],
    queryFn: () => getTaxStat({ from: statFrom, to: statTo }),
  })
  const { data: customers } = useQuery({
    queryKey: ['basedata', 'customers', 'enabled'],
    queryFn: () => getEnabledCustomers(),
  })
  const { data: pendingList, isLoading: pendingLoading } = useQuery({
    queryKey: ['invoice', 'toBeInvoiced', applyCustomer],
    queryFn: () => listToBeInvoiced(applyCustomer),
    enabled: applyOpen,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['invoice', 'sales'] })
    queryClient.invalidateQueries({ queryKey: ['invoice', 'toBeInvoiced'] })
    queryClient.invalidateQueries({ queryKey: ['invoice', 'taxStat'] })
  }

  const applyMutation = useMutation({
    mutationFn: (values: ApplyFormValues) => applySalesFromOrder(selectedOutstock!, { taxRate: values.taxRate }),
    onSuccess: () => {
      message.success('开票申请已生成（待开票）')
      setApplyOpen(false)
      invalidate()
    },
  })

  const issueMutation = useMutation({
    mutationFn: issueSalesInvoice,
    onSuccess: () => {
      message.success('开票成功')
      invalidate()
    },
  })

  const reverseMutation = useMutation({
    mutationFn: reverseSalesInvoice,
    onSuccess: () => {
      message.success('红冲成功，已生成负数红字发票')
      invalidate()
    },
  })

  const openApply = () => {
    form.resetFields()
    form.setFieldsValue({ taxRate: 13 })
    setApplyCustomer(undefined)
    setSelectedOutstock(undefined)
    setApplyOpen(true)
  }

  const columns: ColumnsType<SalesInvoiceVO> = [
    {
      title: '发票号码',
      dataIndex: 'invoiceNo',
      width: 110,
      render: (v: string, row) => (
        <Space size={4}>
          <span>{v}</span>
          {row.redFlag === 1 && <Tag color="red">红字</Tag>}
        </Space>
      ),
    },
    { title: '开票日期', dataIndex: 'invoiceDate', width: 110, render: (v: string | null) => v ?? '-' },
    {
      title: '购方客户',
      dataIndex: 'customerName',
      width: 150,
      render: (v: string | null, row) => v ?? `客户 #${row.customerId}`,
    },
    { title: '业务单号', dataIndex: 'bizNo', width: 150 },
    { title: '金额(不含税)', dataIndex: 'amount', align: 'right', width: 120, render: money },
    { title: '税率', dataIndex: 'taxRate', align: 'center', width: 80, render: rate },
    { title: '税额', dataIndex: 'taxAmount', align: 'right', width: 110, render: money },
    {
      title: '价税合计',
      dataIndex: 'totalAmount',
      align: 'right',
      width: 120,
      render: (v: number) => <span style={{ color: v < 0 ? '#cf1322' : undefined }}>{money(v)}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: InvoiceStatus) => <Tag color={STATUS_TAG[v].color}>{STATUS_TAG[v].text}</Tag>,
    },
    {
      title: '红冲原票',
      dataIndex: 'sourceInvoiceNo',
      width: 110,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 130,
      render: (_, row) => (
        <Space size={4}>
          {row.status === 0 && (
            <HasPerm code="invoice:sales:audit">
              <Popconfirm title="确认对该发票开票？" onConfirm={() => issueMutation.mutate(row.id)}>
                <Button type="link" size="small">
                  开票
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 10 && row.redFlag === 0 && (
            <HasPerm code="invoice:sales:audit">
              <Popconfirm
                title={`确认红冲发票 ${row.invoiceNo}？`}
                description="将生成等额负数红字发票，原票状态置为已红冲"
                onConfirm={() => reverseMutation.mutate(row.id)}
              >
                <Button type="link" size="small" danger>
                  红冲
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  const pendingColumns: ColumnsType<ToBeInvoicedVO> = [
    { title: '出库单号', dataIndex: 'outNo', width: 150 },
    { title: '出库日期', dataIndex: 'outDate', width: 105 },
    {
      title: '客户',
      dataIndex: 'customerName',
      ellipsis: true,
      render: (v: string | null, row) => v ?? `客户 #${row.customerId}`,
    },
    { title: '金额(不含税)', dataIndex: 'amount', align: 'right', width: 120, render: money },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        销项发票
      </Title>

      {/* 本年期间税负指标卡（服务端统计口径：开票日期归属期间） */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title={`不含税销售额（本年）`}
              value={taxStat?.salesAmount ?? 0}
              precision={2}
              loading={!taxStat}
            />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="销项税额"
              value={taxStat?.salesTax ?? 0}
              precision={2}
              valueStyle={{ color: '#1668dc' }}
              loading={!taxStat}
            />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="进项税额(已认证)"
              value={taxStat?.purchaseTax ?? 0}
              precision={2}
              loading={!taxStat}
            />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="应纳增值税"
              value={taxStat?.vatPayable ?? 0}
              precision={2}
              valueStyle={{ color: (taxStat?.vatPayable ?? 0) < 0 ? '#3f8600' : undefined }}
              loading={!taxStat}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card size="small">
            <Statistic
              title="税负率"
              value={taxStat?.taxBurdenRate ?? 0}
              precision={2}
              suffix="%"
              loading={!taxStat}
            />
          </Card>
        </Col>
      </Row>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="发票号码 / 业务单号模糊搜索"
            style={{ width: 240 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setKeyword(v)
            }}
          />
          <Select<number | undefined>
            style={{ width: 200 }}
            placeholder="购方客户"
            allowClear
            showSearch
            optionFilterProp="label"
            value={customerId}
            onChange={(v) => {
              setCustomerId(v)
              setPageNum(1)
            }}
            options={(customers ?? []).map((c) => ({ label: c.name, value: c.id }))}
          />
          <Select<InvoiceStatus>
            style={{ width: 130 }}
            placeholder="状态"
            allowClear
            value={status}
            onChange={(v) => {
              setStatus(v)
              setPageNum(1)
            }}
            options={[
              { value: 0, label: '待开票' },
              { value: 10, label: '已开票' },
              { value: 20, label: '已红冲' },
            ]}
          />
          <HasPerm code="invoice:sales:add">
            <Button type="primary" onClick={openApply}>
              从出库开票
            </Button>
          </HasPerm>
        </Space>

        <Table<SalesInvoiceVO>
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
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

      {/* 从出库开票弹窗：选客户 → 待开票清单选单 → 税率 */}
      <Modal
        title="从销售出库单开票"
        open={applyOpen}
        onCancel={() => setApplyOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={applyMutation.isPending}
        destroyOnClose
        width={680}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => {
            if (selectedOutstock == null) {
              message.warning('请选择要开票的出库单')
              return
            }
            applyMutation.mutate(values)
          }}
        >
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="customerId"
              label="购方客户"
              rules={[{ required: true, message: '请选择客户' }]}
              style={{ width: 260 }}
            >
              <Select
                placeholder="选择客户加载待开票清单"
                showSearch
                optionFilterProp="label"
                onChange={(v: number) => setApplyCustomer(v)}
                options={(customers ?? []).map((c) => ({ label: c.name, value: c.id }))}
              />
            </Form.Item>
            <Form.Item
              name="taxRate"
              label="税率"
              rules={[{ required: true, message: '请选择税率' }]}
              style={{ width: 140 }}
              initialValue={13}
            >
              <Select options={TAX_RATE_OPTIONS} />
            </Form.Item>
          </Space>
          <Form.Item label="待开票出库单（已审核出库，扣除已开票关联）" required>
            <Table<ToBeInvoicedVO>
              size="small"
              rowKey="outstockId"
              columns={pendingColumns}
              dataSource={pendingList ?? []}
              loading={pendingLoading}
              pagination={false}
              scroll={{ y: 260 }}
              locale={{ emptyText: applyCustomer ? '该客户暂无待开票出库单' : '请先选择客户' }}
              rowSelection={{
                type: 'radio',
                selectedRowKeys: selectedOutstock != null ? [selectedOutstock] : [],
                onChange: (keys) => setSelectedOutstock(keys[0] as number | undefined),
              }}
              onRow={(record) => ({
                onClick: () => setSelectedOutstock(record.outstockId),
              })}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
