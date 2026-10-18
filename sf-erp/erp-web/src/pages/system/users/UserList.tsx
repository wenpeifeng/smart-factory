import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import {
  App,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  TreeSelect,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { TableProps } from 'antd'
import {
  getUser,
  pageUsers,
  updateUserScope,
  assignUserRoles,
  listAllRoles,
  getDeptTree,
  type SysUser,
} from '@/api/system'
import { ssoAppStatus, ssoUpdateProfile, ssoResetPassword } from '@/api/auth'
import type { SysDept } from '@/api/system'
import TableEllipsisText from '@/components/TableEllipsisText'

const { Title } = Typography

/** 部门树节点（TreeSelect 数据） */
interface DeptTreeNode {
  title: string
  value: number
  children?: DeptTreeNode[]
}

/** 部门树 → AntD TreeSelect 数据 */
function deptToTreeData(nodes: SysDept[]): DeptTreeNode[] {
  return nodes.map((n) => ({
    title: n.name,
    value: n.id,
    children: n.children.length ? deptToTreeData(n.children) : undefined,
  }))
}

/** 五档数据范围选项 */
const DATA_SCOPES = [
  { label: '全部数据（ALL）', value: 'ALL' },
  { label: '本组织及下级', value: 'ORG_AND_CHILD' },
  { label: '仅本组织', value: 'ORG_ONLY' },
  { label: '仅本部门', value: 'DEPT_ONLY' },
  { label: '仅本人', value: 'SELF' },
]

/**
 * 用户管理页（身份来自认证中心同步副本，与 MES 同款收敛口径）：
 * 分页查询 / 应用级启停（调认证中心） / 分配角色 / 数据范围调整。
 * 建号、离职和删除由认证中心统一管理；本页可维护已分配员工的基础档案并重置统一密码。
 */
export default function UserList() {
  const queryClient = useQueryClient()
  const { message, modal } = App.useApp()

  const [keyword, setKeyword] = useState('')
  const [searchText, setSearchText] = useState('')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [identityStatus, setIdentityStatus] = useState<number>()
  const [entryRange, setEntryRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [sortField, setSortField] = useState('entryDate')
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc')

  // 数据范围 / 分配角色弹窗
  const [scopeTarget, setScopeTarget] = useState<SysUser | null>(null)
  const [scopeForm] = Form.useForm<{ dataScope: string; deptId?: number }>()
  const [roleTarget, setRoleTarget] = useState<SysUser | null>(null)
  const [roleForm] = Form.useForm<{ roleIds: number[] }>()
  const [profileTarget, setProfileTarget] = useState<SysUser | null>(null)
  const [profileForm] = Form.useForm<{ realName: string; position?: string; email?: string; phone?: string }>()
  const [passwordTarget, setPasswordTarget] = useState<SysUser | null>(null)
  const [passwordForm] = Form.useForm<{ password: string; confirmPassword: string }>()

  // 打开弹窗时按详情回显
  useEffect(() => {
    if (roleTarget) {
      getUser(roleTarget.id).then((detail) => roleForm.setFieldsValue({ roleIds: detail.roleIds ?? [] }))
    } else {
      roleForm.resetFields()
    }
  }, [roleTarget, roleForm])

  useEffect(() => {
    if (scopeTarget) {
      getUser(scopeTarget.id).then((detail) =>
        scopeForm.setFieldsValue({ dataScope: detail.dataScope ?? undefined, deptId: detail.deptId ?? undefined }),
      )
    } else {
      scopeForm.resetFields()
    }
  }, [scopeTarget, scopeForm])

  useEffect(() => {
    if (profileTarget) {
      profileForm.setFieldsValue({
        realName: profileTarget.nickname,
        position: profileTarget.position ?? undefined,
        email: profileTarget.email ?? undefined,
        phone: profileTarget.phone ?? undefined,
      })
    } else {
      profileForm.resetFields()
    }
  }, [profileTarget, profileForm])

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['system', 'users', keyword, identityStatus, entryRange?.[0]?.valueOf(), entryRange?.[1]?.valueOf(), sortField, sortOrder, pageNum, pageSize],
    queryFn: () => pageUsers({
      pageNum,
      pageSize,
      keyword: keyword || undefined,
      identityStatus,
      entryDateStart: entryRange?.[0]?.format('YYYY-MM-DD'),
      entryDateEnd: entryRange?.[1]?.format('YYYY-MM-DD'),
      sortField,
      sortOrder,
    }),
  })
  const { data: roles } = useQuery({ queryKey: ['system', 'roles', 'all'], queryFn: listAllRoles })
  const { data: deptTree } = useQuery({ queryKey: ['system', 'depts', 'tree'], queryFn: () => getDeptTree() })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['system', 'users'] })

  const statusMutation = useMutation({
    mutationFn: (u: SysUser) => ssoAppStatus(u.id, u.status !== 1),
    onSuccess: (_, u) => {
      message.success(
        u.status === 1
          ? '应用级禁用完成（认证中心已变更，仅影响本系统）'
          : '应用级启用完成（认证中心已变更）',
      )
      invalidate()
    },
  })

  const scopeMutation = useMutation({
    mutationFn: ({ id, values }: { id: number; values: { dataScope: string; deptId?: number } }) =>
      updateUserScope(id, values),
    onSuccess: () => {
      message.success('数据范围已调整')
      setScopeTarget(null)
      invalidate()
    },
  })

  const roleMutation = useMutation({
    mutationFn: ({ id, roleIds }: { id: number; roleIds: number[] }) => assignUserRoles(id, roleIds),
    onSuccess: () => {
      message.success('角色已分配')
      setRoleTarget(null)
      invalidate()
    },
  })

  const profileMutation = useMutation({
    mutationFn: ({ id, values }: { id: number; values: { realName: string; position?: string; email?: string; phone?: string } }) =>
      ssoUpdateProfile(id, values),
    onSuccess: () => {
      message.success('员工档案已更新，并同步回认证中心')
      setProfileTarget(null)
      invalidate()
    },
  })

  const passwordMutation = useMutation({
    mutationFn: ({ id, password }: { id: number; password: string }) => ssoResetPassword(id, password),
    onSuccess: () => {
      message.success('统一账号密码已重置')
      setPasswordTarget(null)
      passwordForm.resetFields()
    },
  })

  const confirmStatusChange = (record: SysUser) => {
    const disabling = record.status === 1
    modal.confirm({
      centered: true,
      width: 500,
      title: `${disabling ? '禁用' : '启用'} ${record.nickname} 访问 ERP？`,
      content: disabling
        ? '禁用后，该员工将无法登录 ERP；当前已登录会话会立即失效并被踢下线。此操作仅影响 ERP，请确认是否禁用。'
        : '启用后，该员工可重新登录 ERP。请确认其岗位和角色授权符合当前安全要求。',
      okText: `确认${disabling ? '禁用' : '启用'}`,
      cancelText: '取消',
      okButtonProps: { danger: disabling },
      onOk: () => statusMutation.mutateAsync(record),
    })
  }

  const columns: ColumnsType<SysUser> = [
    { title: '姓名', dataIndex: 'nickname', key: 'nickname', width: 110, sorter: true, sortOrder: sortField === 'nickname' ? (sortOrder === 'asc' ? 'ascend' : 'descend') : null, render: (v) => <TableEllipsisText value={v} /> },
    { title: '工号', dataIndex: 'employeeNo', key: 'employeeNo', width: 110, sorter: true, sortOrder: sortField === 'employeeNo' ? (sortOrder === 'asc' ? 'ascend' : 'descend') : null, render: (v) => <TableEllipsisText value={v} /> },
    { title: '账号', dataIndex: 'username', width: 125, render: (v) => <TableEllipsisText value={v} /> },
    { title: '岗位', dataIndex: 'position', width: 130, render: (v) => <TableEllipsisText value={v} /> },
    { title: '联系方式', dataIndex: 'phone', width: 130, render: (v) => <TableEllipsisText value={v} /> },
    { title: '部门', dataIndex: 'deptName', width: 110, render: (v) => <TableEllipsisText value={v} /> },
    { title: '角色', dataIndex: 'roleNames', width: 160, render: (names: string[] | null) => <TableEllipsisText value={names?.join('、')} /> },
    {
      title: '员工状态', dataIndex: 'identityStatus', width: 100,
      render: (v: number | null, record) => v === 2
        ? <Tag color="red">已离职{record.leaveDate ? ` ${record.leaveDate}` : ''}</Tag>
        : v === 3 ? <Tag color="orange">集团停用</Tag> : <Tag color="green">在职</Tag>,
    },
    {
      title: '登录权限', dataIndex: 'status', key: 'status', width: 100, sorter: true,
      sortOrder: sortField === 'status' ? (sortOrder === 'asc' ? 'ascend' : 'descend') : null,
      render: (_, record) => <Switch checked={record.status === 1} checkedChildren="允许" unCheckedChildren="禁用" onChange={() => confirmStatusChange(record)} />,
    },
    {
      title: '入职日期', dataIndex: 'entryDate', key: 'entryDate', width: 120, sorter: true,
      sortOrder: sortField === 'entryDate' ? (sortOrder === 'asc' ? 'ascend' : 'descend') : null,
      render: (v) => v || '-',
    },
    {
      title: '集团建档时间', dataIndex: 'identityCreateTime', key: 'identityCreateTime', width: 160, sorter: true,
      sortOrder: sortField === 'identityCreateTime' ? (sortOrder === 'asc' ? 'ascend' : 'descend') : null,
      render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作', key: 'action', width: 340, fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            disabled={record.identityImmutable}
            title={record.identityImmutable ? '系统种子主档只读' : undefined}
            onClick={() => setProfileTarget(record)}
          >
            编辑档案
          </Button>
          <Button
            type="link"
            size="small"
            disabled={record.identityImmutable}
            title={record.identityImmutable ? '系统种子账号密码只读' : undefined}
            onClick={() => setPasswordTarget(record)}
          >
            重置密码
          </Button>
          <Button type="link" size="small" onClick={() => setScopeTarget(record)}>
            数据范围
          </Button>
          <Button type="link" size="small" onClick={() => setRoleTarget(record)}>
            分配角色
          </Button>
        </Space>
      ),
    },
  ]

  const handleTableChange: TableProps<SysUser>['onChange'] = (_, __, sorter) => {
    const activeSorter = Array.isArray(sorter) ? sorter[0] : sorter
    const nextField = typeof activeSorter.field === 'string' ? activeSorter.field : 'entryDate'
    setSortField(activeSorter.order ? nextField : 'entryDate')
    setSortOrder(activeSorter.order === 'ascend' ? 'asc' : 'desc')
    setPageNum(1)
  }

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        用户管理
      </Title>

      <Card styles={{ body: { padding: 20 } }}>
        <Space wrap size={[10, 10]} style={{ marginBottom: 10 }}>
          <Input.Search
            placeholder="姓名 / 工号 / 账号 / 岗位"
            style={{ width: 280 }}
            allowClear
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            onSearch={(v) => {
              setPageNum(1)
              setKeyword(v)
            }}
          />
          <Select allowClear placeholder="员工状态" style={{ width: 130 }} value={identityStatus} onChange={(v) => { setIdentityStatus(v); setPageNum(1) }} options={[{ label: '在职', value: 1 }, { label: '已离职', value: 2 }, { label: '集团停用', value: 3 }]} />
          <DatePicker.RangePicker value={entryRange} onChange={(v) => { setEntryRange(v); setPageNum(1) }} placeholder={['入职开始', '入职结束']} />
          <Button onClick={() => { setSearchText(''); setKeyword(''); setIdentityStatus(undefined); setEntryRange(null); setSortField('entryDate'); setSortOrder('desc'); setPageNum(1) }}>重置</Button>
        </Space>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 14 }}>
          员工由认证中心实时同步，异常时每分钟自动补偿；默认按入职日期倒序，离职员工始终置底。本系统仅维护档案、密码、访问权限、数据范围和角色授权。
        </Typography.Paragraph>

        <Table<SysUser>
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          scroll={{ x: 1845 }}
          onChange={handleTableChange}
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

      {/* 数据范围弹窗（五档数据权限 + 部门） */}
      <Modal
        title={`数据范围 - ${scopeTarget?.username ?? ''}`}
        open={!!scopeTarget}
        onCancel={() => setScopeTarget(null)}
        onOk={() =>
          scopeForm.validateFields().then((values) =>
            scopeTarget && scopeMutation.mutate({ id: scopeTarget.id, values }),
          )
        }
        confirmLoading={scopeMutation.isPending}
        destroyOnClose
      >
        <Form form={scopeForm} layout="vertical">
          <Form.Item name="dataScope" label="数据范围" rules={[{ required: true, message: '请选择数据范围' }]}>
            <Select options={DATA_SCOPES} />
          </Form.Item>
          <Form.Item name="deptId" label="所属部门（仅本部门档位生效）">
            <TreeSelect
              placeholder="选择部门"
              allowClear
              treeDefaultExpandAll
              treeData={deptToTreeData(deptTree ?? [])}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`编辑员工档案 - ${profileTarget?.employeeNo ?? ''}`}
        open={!!profileTarget}
        onCancel={() => setProfileTarget(null)}
        onOk={() => profileForm.validateFields().then((values) =>
          profileTarget && profileMutation.mutate({ id: profileTarget.id, values }))}
        confirmLoading={profileMutation.isPending}
        destroyOnClose
      >
        <Form form={profileForm} layout="vertical">
          <Form.Item name="realName" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="position" label="岗位"><Input maxLength={64} /></Form.Item>
          <Form.Item name="phone" label="手机号"><Input maxLength={32} /></Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Typography.Text type="secondary">工号、账号、组织和在职状态仍由认证中心生命周期管理。</Typography.Text>
        </Form>
      </Modal>

      <Modal
        title={`重置统一账号密码 - ${passwordTarget?.employeeNo ?? ''}`}
        open={!!passwordTarget}
        onCancel={() => { setPasswordTarget(null); passwordForm.resetFields() }}
        onOk={() => passwordForm.validateFields().then((values) =>
          passwordTarget && passwordMutation.mutate({ id: passwordTarget.id, password: values.password }))}
        confirmLoading={passwordMutation.isPending}
        destroyOnClose
      >
        <Form form={passwordForm} layout="vertical">
          <Form.Item name="password" label="新密码" rules={[{ required: true }, { min: 6, max: 64 }]}>
            <Input.Password placeholder="6-64 位" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={['password']}
            rules={[{ required: true }, ({ getFieldValue }) => ({ validator(_, value) {
              return !value || getFieldValue('password') === value ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致'))
            } })]}
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>

      {/* 分配角色弹窗（整体覆盖） */}
      <Modal
        title={`分配角色 - ${roleTarget?.username ?? ''}`}
        open={!!roleTarget}
        onCancel={() => setRoleTarget(null)}
        onOk={() =>
          roleForm.validateFields().then((values) =>
            roleTarget && roleMutation.mutate({ id: roleTarget.id, roleIds: values.roleIds ?? [] }),
          )
        }
        confirmLoading={roleMutation.isPending}
        destroyOnClose
      >
        <Form form={roleForm} layout="vertical">
          <Form.Item name="roleIds" label="角色（提交后整体覆盖原角色）">
            <Select
              mode="multiple"
              placeholder="选择角色"
              allowClear
              options={(roles ?? []).map((r) => ({ label: r.name, value: r.id }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
