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
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import {
  createSupplier,
  deleteSupplier,
  getEnabledSettleTypes,
  getSuppliers,
  updateSupplier,
  type Supplier,
} from '@/api/basedata'

const { Title } = Typography

/** 供应商表单值（对应后端 BaseSupplierForm） */
interface SupplierFormValues {
  code?: string
  name: string
  contact?: string
  phone?: string
  address?: string
  creditCode?: string
  settleTypeId?: number | null
  initPayable?: number | null
  status: boolean
  remark?: string
}

/** 供应商管理列表页：结构与客户页对称，期初字段为应付。 */
export default function SupplierList() {
  const queryClient = useQueryClient()
  const [form] = Form.useForm<SupplierFormValues>()

  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Supplier | null>(null)

  const { data: settleTypes = [] } = useQuery({
    queryKey: ['basedata', 'settle-types', 'enabled'],
    queryFn: getEnabledSettleTypes,
  })

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['basedata', 'suppliers', pageNum, pageSize, keyword],
    queryFn: () => getSuppliers({ pageNum, pageSize, keyword: keyword || undefined }),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['basedata', 'suppliers'] })

  const saveMutation = useMutation({
    mutationFn: async (values: SupplierFormValues) => {
      const payload = {
        code: values.code || undefined,
        name: values.name,
        contact: values.contact,
        phone: values.phone,
        address: values.address,
        creditCode: values.creditCode,
        settleTypeId: values.settleTypeId ?? null,
        initPayable: values.initPayable ?? 0,
        status: values.status ? 1 : 0,
        remark: values.remark,
      }
      if (editing) {
        await updateSupplier(editing.id, payload)
      } else {
        await createSupplier(payload)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功（编码留空已自动生成）')
      setOpen(false)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteSupplier(id),
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

  const openEdit = (record: Supplier) => {
    setEditing(record)
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      contact: record.contact ?? undefined,
      phone: record.phone ?? undefined,
      address: record.address ?? undefined,
      creditCode: record.creditCode ?? undefined,
      settleTypeId: record.settleTypeId ?? undefined,
      initPayable: record.initPayable,
      status: record.status === 1,
      remark: record.remark ?? undefined,
    })
    setOpen(true)
  }

  const columns: ColumnsType<Supplier> = [
    { title: '供应商编码', dataIndex: 'code', width: 140 },
    { title: '供应商名称', dataIndex: 'name', width: 240, ellipsis: true },
    { title: '联系人', dataIndex: 'contact', width: 90 },
    { title: '手机号', dataIndex: 'phone', width: 140 },
    { title: '统一社会信用代码', dataIndex: 'creditCode', width: 200 },
    { title: '结算方式', dataIndex: 'settleTypeName', width: 100 },
    { title: '期初应付', dataIndex: 'initPayable', width: 110, align: 'right' },
    { title: '地址', dataIndex: 'address', width: 280, ellipsis: true },
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
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <HasPerm code="basedata:supplier:edit">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
            编辑
          </Button>
          </HasPerm>
          <HasPerm code="basedata:supplier:delete">
          <Popconfirm title="确认删除该供应商？" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
          </HasPerm>
        </Space>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        供应商管理
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
          <HasPerm code="basedata:supplier:add">
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增供应商
          </Button>
          </HasPerm>
        </Space>

        <Table<Supplier>
          className="erp-nowrap-table"
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          tableLayout="fixed"
          scroll={{ x: 1540 }}
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
        title={editing ? `编辑供应商：${editing.code}` : '新增供应商'}
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
          initialValues={{ status: true, initPayable: 0 }}
        >
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="code"
                label="供应商编码"
                extra={editing ? undefined : '留空自动生成（GYS+日期+流水）'}
              >
                <Input maxLength={40} placeholder="留空自动生成" disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="name"
                label="供应商名称"
                rules={[{ required: true, message: '请输入供应商名称' }]}
              >
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
              <Form.Item name="initPayable" label="期初应付">
                <InputNumber min={0} style={{ width: '100%' }} precision={2} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="status" label="状态" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="停用" />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="address" label="地址">
                <Input maxLength={200} />
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
