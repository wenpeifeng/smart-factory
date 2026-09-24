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
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  TreeSelect,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  employeeApi,
  supportApi,
  type DeptBrief,
  type EmployeeRow,
} from '@/api/hr'

const { Title } = Typography

/** 部门树节点（TreeSelect 数据） */
interface DeptTreeNode {
  title: string
  value: number
  children?: DeptTreeNode[]
}

/** 部门平铺列表 → AntD TreeSelect 数据 */
function buildDeptTree(depts: DeptBrief[]): DeptTreeNode[] {
  const byParent = new Map<number, DeptBrief[]>()
  for (const d of depts) {
    const list = byParent.get(d.parentId) ?? []
    list.push(d)
    byParent.set(d.parentId, list)
  }
  const toNode = (d: DeptBrief): DeptTreeNode => {
    const children = byParent.get(d.id)
    return { title: d.name, value: d.id, children: children?.length ? children.map(toNode) : undefined }
  }
  return (byParent.get(0) ?? []).map(toNode)
}

/** 员工表单值（dayjs 收敛为提交字符串） */
interface EmployeeFormValues {
  name: string
  gender: number
  phone?: string
  idCard?: string
  deptId?: number
  position?: string
  hireDate: dayjs.Dayjs
  baseSalary: number
  userId?: number
  remark?: string
}

/**
 * 员工档案页：分页查询（姓名/部门/状态筛选）、新增/编辑、离职办理、删除。
 * 身份证号后端脱敏返回（前6后4）。
 */
export default function EmployeeList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [deptId, setDeptId] = useState<number | undefined>()
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  // 编辑弹窗（新增时 editing=null）
  const [editing, setEditing] = useState<EmployeeRow | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<EmployeeFormValues>()

  // 离职办理弹窗
  const [resignTarget, setResignTarget] = useState<EmployeeRow | null>(null)
  const [resignForm] = Form.useForm<{ resignDate: dayjs.Dayjs }>()

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['hr', 'employees', keyword, status, deptId, pageNum, pageSize],
    queryFn: () =>
      employeeApi.page({
        pageNum,
        pageSize,
        keyword: keyword || undefined,
        status,
        deptId,
      }),
  })
  const { data: depts } = useQuery({ queryKey: ['hr', 'depts'], queryFn: supportApi.depts })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['hr', 'employees'] })

  const saveMutation = useMutation({
    mutationFn: async (values: EmployeeFormValues) => {
      const payload = {
        ...values,
        phone: values.phone || undefined,
        idCard: values.idCard || undefined,
        position: values.position || undefined,
        deptId: values.deptId ?? undefined,
        userId: values.userId ?? undefined,
        remark: values.remark || undefined,
        hireDate: values.hireDate.format('YYYY-MM-DD'),
      }
      if (editing) {
        await employeeApi.update(editing.id, payload)
      } else {
        await employeeApi.create(payload)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功')
      setOpen(false)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: employeeApi.remove,
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const resignMutation = useMutation({
    mutationFn: ({ id, resignDate }: { id: number; resignDate: string }) =>
      employeeApi.resign(id, resignDate),
    onSuccess: () => {
      message.success('离职办理完成')
      setResignTarget(null)
      invalidate()
    },
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ gender: 1, hireDate: dayjs(), baseSalary: 0 })
    setOpen(true)
  }

  const openEdit = async (row: EmployeeRow) => {
    const detail = await employeeApi.detail(row.id)
    setEditing(detail)
    form.resetFields()
    form.setFieldsValue({
      name: detail.name,
      gender: detail.gender,
      phone: detail.phone ?? undefined,
      idCard: detail.idCard ?? undefined,
      deptId: detail.deptId ?? undefined,
      position: detail.position ?? undefined,
      hireDate: dayjs(detail.hireDate),
      baseSalary: detail.baseSalary,
      userId: detail.userId ?? undefined,
      remark: detail.remark ?? undefined,
    })
    setOpen(true)
  }

  const columns: ColumnsType<EmployeeRow> = [
    { title: '工号', dataIndex: 'empNo', width: 150 },
    { title: '姓名', dataIndex: 'name', width: 100 },
    {
      title: '性别',
      dataIndex: 'gender',
      width: 70,
      render: (v: number) => (v === 2 ? '女' : '男'),
    },
    { title: '手机号', dataIndex: 'phone', width: 125, render: (v) => v ?? '-' },
    { title: '身份证号', dataIndex: 'idCard', width: 180, render: (v) => v ?? '-' },
    { title: '部门', dataIndex: 'deptName', width: 100, render: (v) => v ?? '-' },
    { title: '岗位', dataIndex: 'position', width: 110, render: (v) => v ?? '-' },
    { title: '入职日期', dataIndex: 'hireDate', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 85,
      render: (v: string) =>
        v === 'ON_JOB' ? <Tag color="success">在职</Tag> : <Tag color="default">离职</Tag>,
    },
    { title: '月薪基准', dataIndex: 'baseSalary', width: 100, align: 'right' },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, record) => (
        <Space size={4}>
          <HasPerm code="hr:employee:edit">
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          </HasPerm>
          {record.status === 'ON_JOB' && (
            <HasPerm code="hr:employee:edit">
              <Button type="link" size="small" onClick={() => setResignTarget(record)}>
                离职办理
              </Button>
            </HasPerm>
          )}
          <HasPerm code="hr:employee:delete">
          <Popconfirm title="确认删除该员工档案？" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" size="small" danger>
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
        员工档案
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Input.Search
            placeholder="姓名模糊搜索"
            style={{ width: 200 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setKeyword(v)
            }}
          />
          <TreeSelect
            placeholder="部门"
            style={{ width: 160 }}
            allowClear
            treeDefaultExpandAll
            treeData={buildDeptTree(depts ?? [])}
            value={deptId}
            onChange={(v) => {
              setPageNum(1)
              setDeptId(v)
            }}
          />
          <Select
            placeholder="状态"
            style={{ width: 110 }}
            allowClear
            options={[
              { label: '在职', value: 'ON_JOB' },
              { label: '离职', value: 'RESIGNED' },
            ]}
            value={status}
            onChange={(v) => {
              setPageNum(1)
              setStatus(v)
            }}
          />
          <HasPerm code="hr:employee:add">
          <Button type="primary" onClick={openCreate}>
            新增员工
          </Button>
          </HasPerm>
        </Space>

        <Table<EmployeeRow>
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

      {/* 新增 / 修改弹窗 */}
      <Modal
        title={editing ? `修改员工 - ${editing.empNo}` : '新增员工'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
        width={620}
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="name"
              label="姓名"
              rules={[{ required: true, message: '请输入姓名' }]}
              style={{ width: 240 }}
            >
              <Input placeholder="姓名" maxLength={30} />
            </Form.Item>
            <Form.Item
              name="gender"
              label="性别"
              rules={[{ required: true, message: '请选择性别' }]}
              style={{ width: 240 }}
            >
              <Select
                options={[
                  { label: '男', value: 1 },
                  { label: '女', value: 2 },
                ]}
              />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="phone"
              label="手机号"
              rules={[{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }]}
              style={{ width: 240 }}
            >
              <Input placeholder="手机号" maxLength={11} />
            </Form.Item>
            <Form.Item
              name="idCard"
              label="身份证号"
              rules={[
                {
                  pattern: /^(\d{15}|\d{17}[0-9Xx])$/,
                  message: '15 或 18 位身份证号',
                },
              ]}
              extra="保存后脱敏展示"
              style={{ width: 240 }}
            >
              <Input placeholder="身份证号" maxLength={18} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="deptId" label="部门" style={{ width: 240 }}>
              <TreeSelect
                placeholder="选择部门"
                allowClear
                treeDefaultExpandAll
                treeData={buildDeptTree(depts ?? [])}
              />
            </Form.Item>
            <Form.Item name="position" label="岗位" style={{ width: 240 }}>
              <Input placeholder="如：冲压工" maxLength={50} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="hireDate"
              label="入职日期"
              rules={[{ required: true, message: '请选择入职日期' }]}
              style={{ width: 240 }}
            >
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="baseSalary"
              label="月薪基准（元）"
              rules={[{ required: true, message: '请输入月薪基准' }]}
              extra="缺勤扣款 = 月薪基准 / 30 × 缺勤天数"
              style={{ width: 240 }}
            >
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="userId"
              label="关联登录账号 ID"
              extra="计件归集按工单制单人归属员工"
              style={{ width: 240 }}
            >
              <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="sys_user.id" />
            </Form.Item>
            <Form.Item name="remark" label="备注" style={{ width: 240 }}>
              <Input placeholder="备注" maxLength={200} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* 离职办理弹窗（单向操作） */}
      <Modal
        title={`离职办理 - ${resignTarget?.empNo ?? ''} ${resignTarget?.name ?? ''}`}
        open={!!resignTarget}
        onCancel={() => setResignTarget(null)}
        onOk={() =>
          resignForm.validateFields().then((values) =>
            resignTarget &&
            resignMutation.mutate({
              id: resignTarget.id,
              resignDate: values.resignDate.format('YYYY-MM-DD'),
            }),
          )
        }
        confirmLoading={resignMutation.isPending}
        destroyOnClose
      >
        <Form form={resignForm} layout="vertical" initialValues={{ resignDate: dayjs() }}>
          <Form.Item
            name="resignDate"
            label="离职日期"
            rules={[{ required: true, message: '请选择离职日期' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
