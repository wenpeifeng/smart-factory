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
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  MAINTAIN_TYPE_OPTIONS,
  createPlan,
  deletePlan,
  executePlan,
  fetchDeviceOptions,
  fetchPlanPage,
  updatePlan,
  type PlanPayload,
  type PlanRow,
} from '@/api/equipment'

const { Text } = Typography

/** 计划新增/编辑表单值（lastDate 为 Dayjs） */
interface PlanFormValues {
  deviceId: number
  maintainType: number
  cycleDays: number
  lastDate?: Dayjs
  remark?: string
}

/** 执行保养表单值 */
interface ExecuteFormValues {
  executor?: string
  content?: string
}

/** 到期单元格：已逾期红色 / 7 天内橙色 / 其余默认 */
function dueTag(record: PlanRow) {
  if (record.nextDate == null || record.daysLeft == null) {
    return <Tag>未排期</Tag>
  }
  if (record.overdue) {
    return (
      <Tag color="red">
        {record.nextDate} 已逾期 {Math.abs(record.daysLeft)} 天
      </Tag>
    )
  }
  if (record.daysLeft <= 7) {
    return (
      <Tag color="orange">
        {record.nextDate} {record.daysLeft} 天后到期
      </Tag>
    )
  }
  return (
    <Text type="secondary">
      {record.nextDate}（{record.daysLeft} 天后）
    </Text>
  )
}

/**
 * 保养计划面板：计划列表（到期高亮）+ 执行（顺延一个周期）+ CRUD。
 * 「本月内到期/已逾期」快捷筛选对应后端 dueOnly。
 */
export default function PlanPanel() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [dueOnly, setDueOnly] = useState(false)
  const [pagination, setPagination] = useState({ pageNum: 1, pageSize: 10 })

  const [editing, setEditing] = useState<PlanRow | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<PlanFormValues>()

  const [executeTarget, setExecuteTarget] = useState<PlanRow | null>(null)
  const [executeForm] = Form.useForm<ExecuteFormValues>()

  const { data: deviceOptions } = useQuery({
    queryKey: ['equipment', 'deviceOptions'],
    queryFn: fetchDeviceOptions,
    staleTime: 5 * 60 * 1000,
  })
  const devOptions = (deviceOptions ?? []).map((d) => ({
    label: `${d.code} ${d.name}`,
    value: d.id,
  }))

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['equipment', 'plans', dueOnly, pagination],
    queryFn: () => fetchPlanPage({ ...pagination, dueOnly: dueOnly || undefined }),
    placeholderData: keepPreviousData,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['equipment'] })

  const saveMutation = useMutation({
    mutationFn: async (values: PlanFormValues) => {
      const payload: PlanPayload = {
        deviceId: values.deviceId,
        maintainType: values.maintainType,
        cycleDays: values.cycleDays,
        lastDate: values.lastDate?.format('YYYY-MM-DD'),
        remark: values.remark,
      }
      if (editing) {
        await updatePlan(editing.id, payload)
      } else {
        await createPlan(payload)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功')
      setOpen(false)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deletePlan,
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
  })

  const executeMutation = useMutation({
    mutationFn: ({ id, values }: { id: number; values: ExecuteFormValues }) =>
      executePlan(id, {
        executor: values.executor,
        content: values.content,
      }),
    onSuccess: () => {
      message.success('保养已执行，下次保养日期已顺延一个周期')
      setExecuteTarget(null)
      executeForm.resetFields()
      invalidate()
    },
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setOpen(true)
  }

  const openEdit = (record: PlanRow) => {
    setEditing(record)
    form.resetFields()
    form.setFieldsValue({
      deviceId: record.deviceId,
      maintainType: record.maintainType,
      cycleDays: record.cycleDays,
      lastDate: record.lastDate ? dayjs(record.lastDate) : undefined,
      remark: record.remark ?? undefined,
    })
    setOpen(true)
  }

  const columns: ColumnsType<PlanRow> = [
    { title: '设备编号', dataIndex: 'deviceCode', width: 140, render: (v) => v ?? '-' },
    { title: '设备名称', dataIndex: 'deviceName', width: 160, ellipsis: true, render: (v) => v ?? '-' },
    {
      title: '保养类型',
      dataIndex: 'maintainTypeLabel',
      width: 100,
      render: (v, r) => v ?? MAINTAIN_TYPE_OPTIONS.find((o) => o.value === r.maintainType)?.label ?? '-',
    },
    {
      title: '周期',
      dataIndex: 'cycleDays',
      width: 90,
      align: 'right',
      render: (v: number) => `${v} 天`,
    },
    { title: '上次保养', dataIndex: 'lastDate', width: 110, render: (v) => v ?? '-' },
    {
      title: '下次保养 / 到期状态',
      key: 'due',
      width: 230,
      render: (_, record) => dueTag(record),
    },
    { title: '保养内容', dataIndex: 'remark', ellipsis: true, render: (v) => v ?? '-' },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button type="link" size="small" onClick={() => setExecuteTarget(record)}>
            执行
          </Button>
          <Popconfirm title="确认删除该保养计划？" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Card size="small">
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          style={{ width: 160 }}
          placeholder="到期筛选"
          value={dueOnly ? 'due' : 'all'}
          onChange={(v) => {
            setDueOnly(v === 'due')
            setPagination((p) => ({ ...p, pageNum: 1 }))
          }}
          options={[
            { label: '全部计划', value: 'all' },
            { label: '已到期/7天内', value: 'due' },
          ]}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新增保养计划
        </Button>
      </Space>

      <Table<PlanRow>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading || isFetching}
        locale={{ emptyText: '暂无保养计划' }}
        scroll={{ x: 1180 }}
        pagination={{
          total: data?.total ?? 0,
          current: data?.pageNum ?? 1,
          pageSize: data?.pageSize ?? 10,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (pageNum, pageSize) => setPagination({ pageNum, pageSize }),
        }}
      />

      {/* 新增 / 修改计划 */}
      <Modal
        title={editing ? '修改保养计划' : '新增保养计划'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Form.Item
            name="deviceId"
            label="设备"
            rules={[{ required: true, message: '请选择设备' }]}
          >
            <Select placeholder="选择设备" options={devOptions} showSearch optionFilterProp="label" />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="maintainType"
              label="保养类型"
              initialValue={1}
              rules={[{ required: true, message: '请选择保养类型' }]}
              style={{ width: 240 }}
            >
              <Select options={MAINTAIN_TYPE_OPTIONS} />
            </Form.Item>
            <Form.Item
              name="cycleDays"
              label="保养周期(天)"
              rules={[{ required: true, message: '请输入周期' }]}
              style={{ width: 240 }}
            >
              <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="如 30" />
            </Form.Item>
          </Space>
          <Form.Item
            name="lastDate"
            label="上次保养日期"
            extra="留空则以今天为周期起点；下次保养日期 = 上次 + 周期（服务端计算）"
          >
            <DatePicker style={{ width: 240 }} />
          </Form.Item>
          <Form.Item name="remark" label="保养内容">
            <Input.TextArea placeholder="如：导轨润滑/丝杠清洁/冷却液更换" maxLength={200} rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 执行保养 */}
      <Modal
        title={`执行保养 - ${executeTarget?.deviceCode ?? ''} ${executeTarget?.maintainTypeLabel ?? ''}`}
        open={!!executeTarget}
        onCancel={() => setExecuteTarget(null)}
        onOk={() => executeForm.submit()}
        confirmLoading={executeMutation.isPending}
        okText="确认执行"
        destroyOnClose
      >
        <Form
          form={executeForm}
          layout="vertical"
          onFinish={(values) =>
            executeTarget && executeMutation.mutate({ id: executeTarget.id, values })
          }
        >
          <Form.Item name="executor" label="执行人">
            <Input placeholder="执行人姓名（留空记系统）" maxLength={50} />
          </Form.Item>
          <Form.Item name="content" label="保养内容">
            <Input.TextArea placeholder="本次实际保养内容（选填）" maxLength={500} rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
