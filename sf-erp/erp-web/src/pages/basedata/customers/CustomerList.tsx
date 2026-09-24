import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { DeleteOutlined, EditOutlined, PlusOutlined, PrinterOutlined } from '@ant-design/icons'
import { usePrint } from '@/hooks/usePrint'
import {
  createCustomer,
  deleteCustomer,
  getCustomers,
  getEnabledSettleTypes,
  updateCustomer,
  type Customer,
} from '@/api/basedata'

const { Title } = Typography

/** 客户表单值（对应后端 BaseCustomerForm） */
interface CustomerFormValues {
  code?: string
  name: string
  contact?: string
  phone?: string
  address?: string
  creditCode?: string
  settleTypeId?: number | null
  creditLimit?: number | null
  initReceivable?: number | null
  level?: string
  status: boolean
  remark?: string
}

/** 客户等级选项（V131 价格中心：等级价策略匹配维度） */
const CUSTOMER_LEVEL_OPTIONS = [
  { value: 'A', label: 'A（重点客户）' },
  { value: 'B', label: 'B（普通客户）' },
  { value: 'C', label: 'C（一般客户）' },
]

/** 客户管理列表页：搜索 + 分页表格 + Modal 表单（参照 UserList 模板，保持 ERP 高密度） */
export default function CustomerList() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm<CustomerFormValues>()
  const { openPrint } = usePrint()

  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Customer | null>(null)

  // 结算方式下拉数据
  const { data: settleTypes = [] } = useQuery({
    queryKey: ['basedata', 'settle-types', 'enabled'],
    queryFn: getEnabledSettleTypes,
  })

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['basedata', 'customers', pageNum, pageSize, keyword],
    queryFn: () => getCustomers({ pageNum, pageSize, keyword: keyword || undefined }),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['basedata', 'customers'] })

  const saveMutation = useMutation({
    mutationFn: async (values: CustomerFormValues) => {
      const payload = {
        code: values.code || undefined,
        name: values.name,
        contact: values.contact,
        phone: values.phone,
        address: values.address,
        creditCode: values.creditCode,
        settleTypeId: values.settleTypeId ?? null,
        creditLimit: values.creditLimit ?? 0,
        initReceivable: values.initReceivable ?? 0,
        level: values.level ?? 'B',
        status: values.status ? 1 : 0,
        remark: values.remark,
      }
      if (editing) {
        await updateCustomer(editing.id, payload)
      } else {
        await createCustomer(payload)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功（编码留空已自动生成）')
      setOpen(false)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteCustomer(id),
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setOpen(true)
  }

  const openEdit = (record: Customer) => {
    setEditing(record)
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      contact: record.contact ?? undefined,
      phone: record.phone ?? undefined,
      address: record.address ?? undefined,
      creditCode: record.creditCode ?? undefined,
      settleTypeId: record.settleTypeId ?? undefined,
      creditLimit: record.creditLimit,
      initReceivable: record.initReceivable,
      level: record.level ?? 'B',
      status: record.status === 1,
      remark: record.remark ?? undefined,
    })
    setOpen(true)
  }

  const columns: ColumnsType<Customer> = [
    { title: '客户编码', dataIndex: 'code', width: 120 },
    { title: '客户名称', dataIndex: 'name', width: 180, ellipsis: true },
    { title: '联系人', dataIndex: 'contact', width: 90 },
    { title: '手机号', dataIndex: 'phone', width: 120 },
    { title: '统一社会信用代码', dataIndex: 'creditCode', width: 170 },
    { title: '结算方式', dataIndex: 'settleTypeName', width: 90 },
    { title: '信用额度', dataIndex: 'creditLimit', width: 110, align: 'right' },
    { title: '期初应收', dataIndex: 'initReceivable', width: 100, align: 'right' },
    {
      title: '等级',
      dataIndex: 'level',
      width: 70,
      render: (v: string) => (
        <Tag color={v === 'A' ? 'gold' : v === 'C' ? 'default' : 'blue'}>{v ?? 'B'}</Tag>
      ),
    },
    { title: '地址', dataIndex: 'address', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number) =>
        v === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 210,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <HasPerm code="basedata:customer:edit">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
            编辑
          </Button>
          </HasPerm>
          <HasPerm code="basedata:customer:delete">
          <Popconfirm title="确认删除该客户？" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
          </HasPerm>
          <HasPerm code="basedata:customer:list">
            <Button
              type="link"
              size="small"
              icon={<PrinterOutlined />}
              onClick={() => openPrint('statement', record.id)}
            >
              对账单
            </Button>
          </HasPerm>
        </Space>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        客户管理
      </Title>

      <Card size="small">
        <Space wrap style={{ marginBottom: 12 }}>
          <Input.Search
            placeholder="编码 / 名称 / 联系人"
            style={{ width: 240 }}
            allowClear
            onSearch={(v) => {
              setKeyword(v)
              setPageNum(1)
            }}
          />
          <HasPerm code="basedata:customer:add">
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增客户
          </Button>
          </HasPerm>
        </Space>

        <Table<Customer>
          rowKey="id"
          size="small"
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
            onChange: (page, size) => {
              setPageNum(page)
              setPageSize(size)
            },
          }}
        />
      </Card>

      <Modal
        title={editing ? `编辑客户：${editing.code}` : '新增客户'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        width={680}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
          initialValues={{ status: true, creditLimit: 0, initReceivable: 0 }}
        >
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="code"
                label="客户编码"
                extra={editing ? undefined : '留空自动生成（KH+日期+流水）'}
              >
                <Input maxLength={40} placeholder="留空自动生成" disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="name" label="客户名称" rules={[{ required: true, message: '请输入客户名称' }]}>
                <Input maxLength={100} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="contact" label="联系人">
                <Input maxLength={50} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="phone"
                label="手机号"
                rules={[{ pattern: /^1\d{10}$/, message: '手机号格式不正确' }]}
              >
                <Input maxLength={11} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="creditCode"
                label="统一社会信用代码"
                rules={[{ len: 18, message: '统一社会信用代码应为18位' }]}
              >
                <Input maxLength={18} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="settleTypeId" label="结算方式">
                <Select
                  allowClear
                  placeholder="选择结算方式"
                  options={settleTypes.map((t) => ({ value: t.id, label: t.name }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="creditLimit" label="信用额度">
                <InputNumber min={0} style={{ width: '100%' }} precision={2} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="initReceivable" label="期初应收">
                <InputNumber min={0} style={{ width: '100%' }} precision={2} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="level" label="客户等级" initialValue="B" extra="等级价策略匹配维度（定价引擎）">
                <Select options={CUSTOMER_LEVEL_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="address" label="地址">
                <Input maxLength={200} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="status" label="状态" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="停用" />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="remark" label="备注">
                <Input.TextArea rows={2} maxLength={200} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  )
}
