import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import dayjs, { type Dayjs } from 'dayjs'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  DEVICE_STATUS_COLOR,
  DEVICE_STATUS_OPTIONS,
  changeDeviceStatus,
  createDevice,
  deleteDevice,
  fetchDevicePage,
  reportRepair,
  updateDevice,
  fmtMoney,
  type DevicePayload,
  type DeviceRow,
} from '@/api/equipment'

const { Title } = Typography

/** 新增/编辑表单值（与后端 DeviceForm 对齐；purchaseDate 为 DatePicker 的 Dayjs 值） */
interface DeviceFormValues {
  code?: string
  name: string
  model?: string
  manufacturer?: string
  purchaseDate?: Dayjs
  originalValue: number
  workshop?: string
  status: number
  responsiblePerson?: string
  checkPeriodDays?: number
  remark?: string
}

/** 报修表单值 */
interface RepairFormValues {
  faultDesc: string
  reporter?: string
  remark?: string
}

/** 状态流转矩阵（与后端一致：三态互通，报废单向终态） */
const NEXT_STATUS: Record<number, { label: string; value: number }[]> = {
  1: [
    { label: '停机', value: 2 },
    { label: '维修中', value: 3 },
    { label: '报废（不可恢复）', value: 4 },
  ],
  2: [
    { label: '运行', value: 1 },
    { label: '维修中', value: 3 },
    { label: '报废（不可恢复）', value: 4 },
  ],
  3: [
    { label: '运行', value: 1 },
    { label: '停机', value: 2 },
    { label: '报废（不可恢复）', value: 4 },
  ],
  4: [],
}

/**
 * 设备台账页：分页查询 / 新增修改 / 状态流转 Tag / 报修入口。
 * 服务端态走 TanStack Query；写操作成功后失效列表缓存自动刷新。
 */
export default function DeviceList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [filters, setFilters] = useState<{ status?: number; keyword: string }>({ keyword: '' })
  const [pagination, setPagination] = useState({ pageNum: 1, pageSize: 10 })

  // 编辑弹窗（新增时 editing=null）
  const [editing, setEditing] = useState<DeviceRow | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<DeviceFormValues>()

  // 报修弹窗
  const [repairTarget, setRepairTarget] = useState<DeviceRow | null>(null)
  const [repairForm] = Form.useForm<RepairFormValues>()

  // 状态流转弹窗
  const [statusTarget, setStatusTarget] = useState<DeviceRow | null>(null)
  const [statusForm] = Form.useForm<{ status: number }>()

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['equipment', 'devices', filters, pagination],
    queryFn: () =>
      fetchDevicePage({
        ...pagination,
        status: filters.status,
        keyword: filters.keyword || undefined,
      }),
    placeholderData: keepPreviousData,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['equipment'] })

  const saveMutation = useMutation({
    mutationFn: async (values: DeviceFormValues) => {
      // 编号由后端生成/库内保持；日期 Dayjs → yyyy-MM-dd
      const payload: DevicePayload = {
        name: values.name,
        model: values.model,
        manufacturer: values.manufacturer,
        purchaseDate: values.purchaseDate?.format('YYYY-MM-DD'),
        originalValue: values.originalValue,
        workshop: values.workshop,
        status: values.status,
        responsiblePerson: values.responsiblePerson,
        checkPeriodDays: values.checkPeriodDays,
        remark: values.remark,
      }
      if (editing) {
        await updateDevice(editing.id, payload)
      } else {
        await createDevice(payload)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功')
      setOpen(false)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteDevice,
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const repairMutation = useMutation({
    mutationFn: (values: RepairFormValues) =>
      reportRepair({
        deviceId: repairTarget!.id,
        faultDesc: values.faultDesc,
        reporter: values.reporter,
        remark: values.remark,
      }),
    onSuccess: () => {
      message.success(`已报修：${repairTarget?.name}，工单待维修`)
      setRepairTarget(null)
      invalidate()
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: number }) => changeDeviceStatus(id, status),
    onSuccess: () => {
      message.success('状态已流转')
      setStatusTarget(null)
      invalidate()
    },
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ status: 1, originalValue: 0 })
    setOpen(true)
  }

  const openEdit = (record: DeviceRow) => {
    setEditing(record)
    form.resetFields()
    form.setFieldsValue({
      name: record.name,
      model: record.model ?? undefined,
      manufacturer: record.manufacturer ?? undefined,
      purchaseDate: record.purchaseDate ? dayjs(record.purchaseDate) : undefined,
      originalValue: record.originalValue,
      workshop: record.workshop ?? undefined,
      status: record.status <= 2 ? record.status : 1,
      responsiblePerson: record.responsiblePerson ?? undefined,
      checkPeriodDays: record.checkPeriodDays ?? undefined,
      remark: record.remark ?? undefined,
    })
    setOpen(true)
  }

  const columns: ColumnsType<DeviceRow> = [
    { title: '设备编号', dataIndex: 'code', width: 140 },
    { title: '设备名称', dataIndex: 'name', width: 160, ellipsis: true },
    { title: '型号', dataIndex: 'model', width: 110, render: (v) => v ?? '-' },
    { title: '制造商', dataIndex: 'manufacturer', width: 100, ellipsis: true, render: (v) => v ?? '-' },
    { title: '所在车间', dataIndex: 'workshop', width: 120, ellipsis: true, render: (v) => v ?? '-' },
    {
      title: '原值(元)',
      dataIndex: 'originalValue',
      width: 110,
      align: 'right',
      render: (v: number) => fmtMoney(v),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_, record) => (
        <Tag color={DEVICE_STATUS_COLOR[record.status]}>{record.statusLabel}</Tag>
      ),
    },
    { title: '责任人', dataIndex: 'responsiblePerson', width: 90, render: (v) => v ?? '-' },
    {
      title: '点检周期',
      dataIndex: 'checkPeriodDays',
      width: 90,
      align: 'right',
      render: (v: number | null) => (v != null ? `${v} 天` : '-'),
    },
    { title: '购置日期', dataIndex: 'purchaseDate', width: 110, render: (v) => v ?? '-' },
    {
      title: '操作',
      key: 'action',
      width: 230,
      fixed: 'right',
      render: (_, record) => {
        const nextOptions = NEXT_STATUS[record.status] ?? []
        return (
          <Space size={4}>
            <HasPerm code="equipment:device:edit">
              <Button type="link" size="small" onClick={() => openEdit(record)}>
                编辑
              </Button>
            </HasPerm>
            {record.status !== 4 && (
              <HasPerm code="equipment:repair:add">
                <Button type="link" size="small" onClick={() => setRepairTarget(record)}>
                  报修
                </Button>
              </HasPerm>
            )}
            {nextOptions.length > 0 && (
              <HasPerm code="equipment:device:edit">
                <Button
                  type="link"
                  size="small"
                  onClick={() => {
                    setStatusTarget(record)
                    statusForm.setFieldsValue({ status: nextOptions[0].value })
                  }}
                >
                  流转
                </Button>
              </HasPerm>
            )}
            <HasPerm code="equipment:device:delete">
            <Popconfirm title="确认删除该设备？" onConfirm={() => deleteMutation.mutate(record.id)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
            </HasPerm>
          </Space>
        )
      },
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        设备台账
      </Title>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            style={{ width: 120 }}
            placeholder="全部状态"
            allowClear
            options={DEVICE_STATUS_OPTIONS}
            value={filters.status}
            onChange={(v) => {
              setFilters((f) => ({ ...f, status: v }))
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <Input.Search
            style={{ width: 240 }}
            placeholder="编号 / 名称 / 型号模糊搜索"
            allowClear
            onSearch={(v) => {
              setFilters((f) => ({ ...f, keyword: v }))
              setPagination((p) => ({ ...p, pageNum: 1 }))
            }}
          />
          <HasPerm code="equipment:device:add">
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增设备
          </Button>
          </HasPerm>
        </Space>

        <Table<DeviceRow>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          locale={{ emptyText: '暂无设备' }}
          scroll={{ x: 1280 }}
          pagination={{
            total: data?.total ?? 0,
            current: data?.pageNum ?? 1,
            pageSize: data?.pageSize ?? 10,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (pageNum, pageSize) => setPagination({ pageNum, pageSize }),
          }}
        />
      </Card>

      {/* 新增 / 修改弹窗 */}
      <Modal
        title={editing ? `修改设备 - ${editing.code}` : '新增设备'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        width={640}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="name"
              label="设备名称"
              rules={[{ required: true, message: '请输入设备名称' }]}
              style={{ width: 280 }}
            >
              <Input placeholder="设备名称" maxLength={100} />
            </Form.Item>
            <Form.Item name="model" label="规格型号" style={{ width: 280 }}>
              <Input placeholder="规格型号" maxLength={100} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="manufacturer" label="制造商" style={{ width: 280 }}>
              <Input placeholder="制造商" maxLength={100} />
            </Form.Item>
            <Form.Item name="workshop" label="所在车间" style={{ width: 280 }}>
              <Input placeholder="所在车间" maxLength={100} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="purchaseDate" label="购置日期" style={{ width: 280 }}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="originalValue"
              label="购置原值(元)"
              rules={[{ required: true, message: '请输入购置原值' }]}
              style={{ width: 280 }}
            >
              <InputNumber
                min={0}
                precision={2}
                style={{ width: '100%' }}
                placeholder="购置原值"
              />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="status"
              label={editing ? '状态（修改不走此入口）' : '初始状态'}
              initialValue={1}
              style={{ width: 280 }}
              extra={editing ? '状态变更请使用列表「流转」操作' : undefined}
            >
              <Select
                options={DEVICE_STATUS_OPTIONS.filter((o) => o.value <= 2)}
                disabled={!!editing}
              />
            </Form.Item>
            <Form.Item name="responsiblePerson" label="责任人" style={{ width: 280 }}>
              <Input placeholder="责任人" maxLength={50} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="checkPeriodDays" label="点检周期(天)" style={{ width: 280 }}>
              <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="留空不点检" />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea placeholder="备注" maxLength={200} rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 报修弹窗 */}
      <Modal
        title={`设备报修 - ${repairTarget?.code ?? ''} ${repairTarget?.name ?? ''}`}
        open={!!repairTarget}
        onCancel={() => setRepairTarget(null)}
        onOk={() => repairForm.submit()}
        confirmLoading={repairMutation.isPending}
        okText="提交报修"
        destroyOnClose
      >
        <Form form={repairForm} layout="vertical" onFinish={(values) => repairMutation.mutate(values)}>
          <Form.Item
            name="faultDesc"
            label="故障描述"
            rules={[{ required: true, message: '请输入故障描述' }]}
          >
            <Input.TextArea placeholder="描述故障现象、发生时间等" maxLength={500} rows={3} />
          </Form.Item>
          <Form.Item name="reporter" label="报修人">
            <Input placeholder="报修人姓名" maxLength={50} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea placeholder="备注（可选）" maxLength={200} rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 状态流转弹窗 */}
      <Modal
        title={`状态流转 - ${statusTarget?.code ?? ''}（当前：${statusTarget?.statusLabel ?? ''}）`}
        open={!!statusTarget}
        onCancel={() => setStatusTarget(null)}
        onOk={() =>
          statusForm.validateFields().then((values) =>
            statusTarget && statusMutation.mutate({ id: statusTarget.id, status: values.status }),
          )
        }
        confirmLoading={statusMutation.isPending}
        destroyOnClose
      >
        <Form form={statusForm} layout="vertical">
          <Form.Item name="status" label="流转到" rules={[{ required: true, message: '请选择目标状态' }]}>
            <Radio.Group
              options={(NEXT_STATUS[statusTarget?.status ?? 4] ?? []).map((o) => ({
                label: o.label,
                value: o.value,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
