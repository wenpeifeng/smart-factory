import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Col,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  ApartmentOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import {
  createLocation,
  createWarehouse,
  deleteLocation,
  deleteWarehouse,
  getLocations,
  getWarehouses,
  updateLocation,
  updateWarehouse,
  type BaseLocation,
  type Warehouse,
} from '@/api/basedata'

const { Title } = Typography

/** 仓库表单值 */
interface WarehouseFormValues {
  code?: string
  name: string
  manager?: string
  address?: string
  status: boolean
  remark?: string
}

/** 库位表单值 */
interface LocationFormValues {
  code: string
  name: string
  capacity?: number | null
  status: boolean
  remark?: string
}

/** 仓库管理页：仓库表格 + 操作列打开库位抽屉（主子表） */
export default function WarehouseList() {
  const queryClient = useQueryClient()
  const [whForm] = Form.useForm<WarehouseFormValues>()
  const [locForm] = Form.useForm<LocationFormValues>()

  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')

  // 仓库弹窗
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Warehouse | null>(null)

  // 库位抽屉
  const [drawerWh, setDrawerWh] = useState<Warehouse | null>(null)
  const [locOpen, setLocOpen] = useState(false)
  const [editingLoc, setEditingLoc] = useState<BaseLocation | null>(null)

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['basedata', 'warehouses', pageNum, pageSize, keyword],
    queryFn: () => getWarehouses({ pageNum, pageSize, keyword: keyword || undefined }),
  })

  // 当前抽屉仓库的库位列表
  const { data: locations = [], isLoading: locLoading } = useQuery({
    queryKey: ['basedata', 'locations', drawerWh?.id],
    queryFn: () => getLocations(drawerWh!.id),
    enabled: drawerWh !== null,
  })

  const invalidateWarehouses = () =>
    queryClient.invalidateQueries({ queryKey: ['basedata', 'warehouses'] })
  const invalidateLocations = () => {
    if (drawerWh) {
      queryClient.invalidateQueries({ queryKey: ['basedata', 'locations', drawerWh.id] })
    }
  }

  // ===================== 仓库 Mutation =====================
  const saveMutation = useMutation({
    mutationFn: async (values: WarehouseFormValues) => {
      const payload = {
        code: values.code || undefined,
        name: values.name,
        manager: values.manager,
        address: values.address,
        status: values.status ? 1 : 0,
        remark: values.remark,
      }
      if (editing) {
        await updateWarehouse(editing.id, payload)
      } else {
        await createWarehouse(payload)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功（编码留空已自动生成）')
      setOpen(false)
      invalidateWarehouses()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteWarehouse(id),
    onSuccess: () => {
      message.success('删除成功')
      invalidateWarehouses()
    },
  })

  // ===================== 库位 Mutation =====================
  const locSaveMutation = useMutation({
    mutationFn: async (values: LocationFormValues) => {
      if (!drawerWh) throw new Error('未选择仓库')
      const payload = {
        warehouseId: drawerWh.id,
        code: values.code,
        name: values.name,
        capacity: values.capacity ?? 0,
        status: values.status ? 1 : 0,
        remark: values.remark,
      }
      if (editingLoc) {
        await updateLocation(editingLoc.id, payload)
      } else {
        await createLocation(payload)
      }
    },
    onSuccess: () => {
      message.success(editingLoc ? '修改成功' : '新增成功')
      setLocOpen(false)
      invalidateLocations()
    },
  })

  const locDeleteMutation = useMutation({
    mutationFn: (id: number) => deleteLocation(id),
    onSuccess: () => {
      message.success('删除成功')
      invalidateLocations()
    },
  })

  // ===================== 弹窗逻辑 =====================
  const openCreate = () => {
    setEditing(null)
    whForm.resetFields()
    setOpen(true)
  }

  const openEdit = (record: Warehouse) => {
    setEditing(record)
    whForm.setFieldsValue({
      code: record.code,
      name: record.name,
      manager: record.manager ?? undefined,
      address: record.address ?? undefined,
      status: record.status === 1,
      remark: record.remark ?? undefined,
    })
    setOpen(true)
  }

  const openCreateLoc = () => {
    setEditingLoc(null)
    locForm.resetFields()
    setLocOpen(true)
  }

  const openEditLoc = (record: BaseLocation) => {
    setEditingLoc(record)
    locForm.setFieldsValue({
      code: record.code,
      name: record.name,
      capacity: record.capacity,
      status: record.status === 1,
      remark: record.remark ?? undefined,
    })
    setLocOpen(true)
  }

  // ===================== 列定义 =====================
  const whColumns: ColumnsType<Warehouse> = [
    { title: '仓库编码', dataIndex: 'code', width: 130 },
    { title: '仓库名称', dataIndex: 'name', width: 160 },
    { title: '负责人', dataIndex: 'manager', width: 100 },
    { title: '地址', dataIndex: 'address', ellipsis: true },
    {
      title: '是否启用',
      dataIndex: 'status',
      width: 90,
      render: (v: number) =>
        v === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>,
    },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <HasPerm code="basedata:warehouse:edit">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
            编辑
          </Button>
          </HasPerm>
          <Button
            type="link"
            size="small"
            icon={<ApartmentOutlined />}
            onClick={() => setDrawerWh(record)}
          >
            库位
          </Button>
          <HasPerm code="basedata:warehouse:delete">
          <Popconfirm title="确认删除该仓库？" description="存在库位时将无法删除" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
          </HasPerm>
        </Space>
      ),
    },
  ]

  const locColumns: ColumnsType<BaseLocation> = [
    { title: '库位编码', dataIndex: 'code', width: 110 },
    { title: '库位名称', dataIndex: 'name', width: 130 },
    { title: '容量', dataIndex: 'capacity', width: 90, align: 'right' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number) =>
        v === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>,
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 130,
      render: (_, record) => (
        <Space size={4}>
          <HasPerm code="basedata:warehouse:edit">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEditLoc(record)}>
            编辑
          </Button>
          </HasPerm>
          <HasPerm code="basedata:warehouse:delete">
          <Popconfirm title="确认删除该库位？" onConfirm={() => locDeleteMutation.mutate(record.id)}>
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
        仓库管理
      </Title>

      <Card size="small">
        <Space wrap style={{ marginBottom: 12 }}>
          <Input.Search
            placeholder="编码 / 名称 / 负责人"
            style={{ width: 240 }}
            allowClear
            onSearch={(v) => {
              setKeyword(v)
              setPageNum(1)
            }}
          />
          <HasPerm code="basedata:warehouse:add">
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增仓库
          </Button>
          </HasPerm>
        </Space>

        <Table<Warehouse>
          rowKey="id"
          size="small"
          columns={whColumns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          scroll={{ x: 1000 }}
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

      {/* 仓库新增/修改弹窗 */}
      <Modal
        title={editing ? `编辑仓库：${editing.code}` : '新增仓库'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => whForm.submit()}
        confirmLoading={saveMutation.isPending}
        width={600}
        destroyOnClose
      >
        <Form
          form={whForm}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
          initialValues={{ status: true }}
        >
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="code"
                label="仓库编码"
                extra={editing ? undefined : '留空自动生成（CK+日期+流水）'}
              >
                <Input maxLength={40} placeholder="留空自动生成" disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="name" label="仓库名称" rules={[{ required: true, message: '请输入仓库名称' }]}>
                <Input maxLength={100} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="manager" label="负责人">
                <Input maxLength={50} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="status" label="是否启用" valuePropName="checked">
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

      {/* 库位抽屉：展示当前仓库下库位并维护 */}
      <Drawer
        title={`库位管理：${drawerWh?.name ?? ''}（${drawerWh?.code ?? ''}）`}
        width={720}
        open={drawerWh !== null}
        onClose={() => setDrawerWh(null)}
      >
        <Button
          type="primary"
          icon={<PlusOutlined />}
          style={{ marginBottom: 12 }}
          onClick={openCreateLoc}
        >
          新增库位
        </Button>
        <Table<BaseLocation>
          rowKey="id"
          size="small"
          columns={locColumns}
          dataSource={locations}
          loading={locLoading}
          pagination={false}
        />
      </Drawer>

      {/* 库位新增/修改弹窗 */}
      <Modal
        title={editingLoc ? `编辑库位：${editingLoc.code}` : '新增库位'}
        open={locOpen}
        onCancel={() => setLocOpen(false)}
        onOk={() => locForm.submit()}
        confirmLoading={locSaveMutation.isPending}
        destroyOnClose
      >
        <Form
          form={locForm}
          layout="vertical"
          onFinish={(values) => locSaveMutation.mutate(values)}
          initialValues={{ status: true, capacity: 0 }}
        >
          <Form.Item name="code" label="库位编码" rules={[{ required: true, message: '请输入库位编码' }]}>
            <Input maxLength={40} placeholder="仓库内唯一，如 A-01-01" />
          </Form.Item>
          <Form.Item name="name" label="库位名称" rules={[{ required: true, message: '请输入库位名称' }]}>
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item name="capacity" label="容量">
            <InputNumber min={0} style={{ width: '100%' }} precision={4} />
          </Form.Item>
          <Form.Item name="status" label="状态" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input maxLength={200} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
