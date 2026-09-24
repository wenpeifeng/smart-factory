import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
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
import {
  getUser,
  pageUsers,
  updateUserScope,
  assignUserRoles,
  listAllRoles,
  getDeptTree,
  type SysUser,
} from '@/api/system'
import { ssoSyncUsers, ssoAppStatus } from '@/api/auth'
import type { SysDept } from '@/api/system'

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
 * 分页查询 / 手动同步 / 应用级启停（调认证中心） / 分配角色 / 数据范围调整。
 * 主档案（建号/改密/离职）在认证中心操作，本页不再提供新增/编辑/删除/重置密码。
 */
export default function UserList() {
  const queryClient = useQueryClient()
  const { message, modal } = App.useApp()

  const [keyword, setKeyword] = useState('')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  // 数据范围 / 分配角色弹窗
  const [scopeTarget, setScopeTarget] = useState<SysUser | null>(null)
  const [scopeForm] = Form.useForm<{ dataScope: string; deptId?: number }>()
  const [roleTarget, setRoleTarget] = useState<SysUser | null>(null)
  const [roleForm] = Form.useForm<{ roleIds: number[] }>()

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

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['system', 'users', keyword, pageNum, pageSize],
    queryFn: () => pageUsers({ pageNum, pageSize, keyword: keyword || undefined }),
  })
  const { data: roles } = useQuery({ queryKey: ['system', 'roles', 'all'], queryFn: listAllRoles })
  const { data: deptTree } = useQuery({ queryKey: ['system', 'depts', 'tree'], queryFn: () => getDeptTree() })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['system', 'users'] })

  const syncMutation = useMutation({
    mutationFn: ssoSyncUsers,
    onSuccess: (r) => {
      message.success(
        `同步完成：共 ${r.total} 人，新增 ${r.added}、更新 ${r.updated}、禁用 ${r.disabled}、孤儿禁用 ${r.orphanDisabled}`,
      )
      invalidate()
    },
  })

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

  const columns: ColumnsType<SysUser> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '账号', dataIndex: 'username', width: 120 },
    { title: '姓名', dataIndex: 'nickname', width: 100 },
    {
      title: '工号',
      dataIndex: 'employeeNo',
      width: 100,
      render: (v) => (v ? <Tag color="blue">{v}</Tag> : <Tag>本地</Tag>),
    },
    { title: '部门', dataIndex: 'deptName', width: 100, render: (v) => v ?? '-' },
    {
      title: '角色',
      dataIndex: 'roleNames',
      ellipsis: true,
      render: (names: string[] | null) =>
        names?.length ? names.map((n) => <Tag key={n}>{n}</Tag>) : '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_, record) => (
        <Switch
          checked={record.status === 1}
          checkedChildren="启用"
          unCheckedChildren="停用"
          onChange={() => {
            modal.confirm({
              title:
                record.status === 1
                  ? '应用级禁用该用户？（调认证中心，仅影响本系统）'
                  : '应用级启用该用户？（调认证中心）',
              onOk: () => statusMutation.mutate(record),
            })
          }}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, record) => (
        <Space size={4}>
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

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        用户管理
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="账号 / 姓名模糊搜索"
            style={{ width: 240 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setKeyword(v)
            }}
          />
          <Button type="primary" loading={syncMutation.isPending} onClick={() => syncMutation.mutate()}>
            从认证中心同步
          </Button>
          <Typography.Text type="secondary">
            用户档案（建号/改密/离职）在统一认证中心维护，此处为同步副本 + 本系统授权
          </Typography.Text>
        </Space>

        <Table<SysUser>
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
