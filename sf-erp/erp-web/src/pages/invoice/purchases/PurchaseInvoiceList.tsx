import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { getEnabledSuppliers } from '@/api/basedata'
import {
  authenticatePurchaseInvoice,
  listToBeRegistered,
  pagePurchaseInvoices,
  registerPurchaseFromInstock,
  reversePurchaseInvoice,
  type InvoiceStatus,
  type PurchaseInvoiceVO,
  type ToBeRegisteredVO,
} from '@/api/invoice'

const { Title } = Typography

/** 进项发票状态 → 标签配色与文案 */
const STATUS_TAG: Record<InvoiceStatus, { color: string; text: string }> = {
  0: { color: 'orange', text: '待认证' },
  10: { color: 'green', text: '已认证' },
  20: { color: 'default', text: '已红冲' },
}

/** 金额展示（右对齐 + 千分位） */
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

/** 登记弹窗表单值 */
interface RegisterFormValues {
  invoiceNo?: string
  invoiceDate?: dayjs.Dayjs
  taxRate?: number
}

/**
 * 进项发票页：
 * 分页列表（供应商/状态/单号筛选）+ 从入库登记弹窗（待登记清单选单，
 * 可填票面发票号/开票日期/税率）+ 认证 / 红冲操作。
 */
export default function PurchaseInvoiceList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [supplierId, setSupplierId] = useState<number | undefined>()
  const [status, setStatus] = useState<InvoiceStatus | undefined>()

  // 登记弹窗
  const [registerOpen, setRegisterOpen] = useState(false)
  const [registerSupplier, setRegisterSupplier] = useState<number | undefined>()
  const [selectedInstock, setSelectedInstock] = useState<number | undefined>()
  const [form] = Form.useForm<RegisterFormValues>()

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['invoice', 'purchases', { pageNum, pageSize, keyword, supplierId, status }],
    queryFn: () =>
      pagePurchaseInvoices({
        pageNum,
        pageSize,
        keyword: keyword || undefined,
        supplierId,
        status,
      }),
  })
  const { data: suppliers } = useQuery({
    queryKey: ['basedata', 'suppliers', 'enabled'],
    queryFn: () => getEnabledSuppliers(),
  })
  const { data: pendingList, isLoading: pendingLoading } = useQuery({
    queryKey: ['invoice', 'toBeRegistered', registerSupplier],
    queryFn: () => listToBeRegistered(registerSupplier),
    enabled: registerOpen,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['invoice', 'purchases'] })
    queryClient.invalidateQueries({ queryKey: ['invoice', 'toBeRegistered'] })
  }

  const registerMutation = useMutation({
    mutationFn: (values: RegisterFormValues) =>
      registerPurchaseFromInstock(selectedInstock!, {
        invoiceNo: values.invoiceNo || undefined,
        invoiceDate: values.invoiceDate ? values.invoiceDate.format('YYYY-MM-DD') : undefined,
        taxRate: values.taxRate,
      }),
    onSuccess: () => {
      message.success('进项发票已登记（待认证）')
      setRegisterOpen(false)
      invalidate()
    },
  })

  const authMutation = useMutation({
    mutationFn: authenticatePurchaseInvoice,
    onSuccess: () => {
      message.success('认证成功，税额已进入税负统计')
      invalidate()
    },
  })

  const reverseMutation = useMutation({
    mutationFn: reversePurchaseInvoice,
    onSuccess: () => {
      message.success('红冲成功，已生成负数红字发票')
      invalidate()
    },
  })

  const openRegister = () => {
    form.resetFields()
    setRegisterSupplier(undefined)
    setSelectedInstock(undefined)
    setRegisterOpen(true)
  }

  const columns: ColumnsType<PurchaseInvoiceVO> = [
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
    { title: '开票日期', dataIndex: 'invoiceDate', width: 105, render: (v: string | null) => v ?? '-' },
    {
      title: '销方供应商',
      dataIndex: 'supplierName',
      width: 160,
      render: (v: string | null, row) => v ?? `供应商 #${row.supplierId}`,
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
    { title: '认证日期', dataIndex: 'authDate', width: 105, render: (v: string | null) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: InvoiceStatus) => <Tag color={STATUS_TAG[v].color}>{STATUS_TAG[v].text}</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 130,
      render: (_, row) => (
        <Space size={4}>
          {row.status === 0 && (
            <HasPerm code="invoice:purchase:audit">
              <Popconfirm title="确认认证该进项发票？" onConfirm={() => authMutation.mutate(row.id)}>
                <Button type="link" size="small">
                  认证
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 10 && row.redFlag === 0 && (
            <HasPerm code="invoice:purchase:audit">
              <Popconfirm
                title={`确认红冲发票 ${row.invoiceNo}？`}
                description="将生成等额负数红字发票冲减已认证税额"
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

  const pendingColumns: ColumnsType<ToBeRegisteredVO> = [
    { title: '入库单号', dataIndex: 'instockNo', width: 150 },
    { title: '入库日期', dataIndex: 'instockDate', width: 105 },
    {
      title: '供应商',
      dataIndex: 'supplierName',
      ellipsis: true,
      render: (v: string | null, row) => v ?? `供应商 #${row.supplierId}`,
    },
    { title: '金额(不含税)', dataIndex: 'amount', align: 'right', width: 120, render: money },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        进项发票
      </Title>

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
            placeholder="销方供应商"
            allowClear
            showSearch
            optionFilterProp="label"
            value={supplierId}
            onChange={(v) => {
              setSupplierId(v)
              setPageNum(1)
            }}
            options={(suppliers ?? []).map((s) => ({ label: s.name, value: s.id }))}
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
              { value: 0, label: '待认证' },
              { value: 10, label: '已认证' },
              { value: 20, label: '已红冲' },
            ]}
          />
          <HasPerm code="invoice:purchase:add">
            <Button type="primary" onClick={openRegister}>
              从入库登记
            </Button>
          </HasPerm>
        </Space>

        <Table<PurchaseInvoiceVO>
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

      {/* 从入库登记弹窗：选供应商 → 待登记清单选单 → 票面信息（可选） */}
      <Modal
        title="从采购入库单登记进项发票"
        open={registerOpen}
        onCancel={() => setRegisterOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={registerMutation.isPending}
        destroyOnClose
        width={680}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => {
            if (selectedInstock == null) {
              message.warning('请选择要登记的入库单')
              return
            }
            registerMutation.mutate(values)
          }}
        >
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="invoiceNo"
              label="票面发票号码"
              extra="8 位数字，留空由系统取号"
              style={{ width: 200 }}
              rules={[{ pattern: /^\d{8}$/, message: '发票号码必须为8位数字' }]}
            >
              <Input placeholder="如 04881234" maxLength={8} />
            </Form.Item>
            <Form.Item name="invoiceDate" label="票面开票日期" extra="留空取入库日期" style={{ width: 180 }}>
              <DatePicker allowClear />
            </Form.Item>
            <Form.Item name="taxRate" label="税率" extra="留空取明细统一税率" style={{ width: 130 }}>
              <Select allowClear options={TAX_RATE_OPTIONS} placeholder="默认" />
            </Form.Item>
          </Space>
          <Form.Item label="待登记入库单（已审核入库，扣除已登记发票）" required>
            <Table<ToBeRegisteredVO>
              size="small"
              rowKey="instockId"
              columns={pendingColumns}
              dataSource={pendingList ?? []}
              loading={pendingLoading}
              pagination={false}
              scroll={{ y: 260 }}
              locale={{ emptyText: registerSupplier ? '该供应商暂无待登记入库单' : '可先按供应商过滤' }}
              rowSelection={{
                type: 'radio',
                selectedRowKeys: selectedInstock != null ? [selectedInstock] : [],
                onChange: (keys) => setSelectedInstock(keys[0] as number | undefined),
              }}
              onRow={(record) => ({
                onClick: () => setSelectedInstock(record.instockId),
              })}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
