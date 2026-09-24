import { syncAuthUsers, changeUserAppStatus } from '@/api/oidcExt'
import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  getUser,
  pageUsers,




  assignUserRoles,
  listAllRoles,
  type SysUser,
} from '@/api/system'

const { Title } = Typography


/**
 * 用户管理页（身份来自 auth-center 同步副本）：分页查询 / 同步 / 应用级启停 / 分配角色。
 * 主档案（建号/改密/离职）在认证中心操作，本页不再提供增删改密。
 * 服务端态全部走 TanStack Query；写操作成功后失效列表缓存自动刷新。
 */
export default function UserList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [keyword, setKeyword] = useState('')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [roleTarget, setRoleTarget] = useState<SysUser | null>(null)
  const [roleForm] = Form.useForm<{ roleIds: number[] }>()

  // 打开分配角色弹窗时按详情回显已选角色
  useEffect(() => {
    if (roleTarget) {
      getUser(roleTarget.id).then((detail) => roleForm.setFieldsValue({ roleIds: detail.roleIds ?? [] }))
    } else {
      roleForm.resetFields()
    }
  }, [roleTarget, roleForm])

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['system', 'users', keyword, pageNum, pageSize],
    queryFn: () => pageUsers({ pageNum, pageSize, keyword: keyword || undefined }),
  })
  const { data: roles } = useQuery({ queryKey: ['system', 'roles', 'all'], queryFn: listAllRoles })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['system', 'users'] })

  // 用户主档案由 auth-center 统一管理：此处只做同步 / 应用级启停 / 角色分配
  const syncMutation = useMutation({
    mutationFn: () => syncAuthUsers(),
    onSuccess: (r) => {
      message.success(`同步完成：拉取 ${r.fetched}，新增 ${r.inserted}，更新 ${r.updated}，禁用 ${r.disabled}`)
      invalidate()
    },
  })

  const statusMutation = useMutation({
    mutationFn: (u: SysUser) =>
      changeUserAppStatus(u.employeeNo ?? u.username, u.status === 1 ? false : true),
    onSuccess: (r) => {
      message.success(`应用级启停完成（中心已变更），本地已刷新：禁用 ${r.disabled}`)
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
    { title: '账号', dataIndex: 'username', width: 130 },
    { title: '姓名', dataIndex: 'nickname', width: 110 },
    { title: '部门', dataIndex: 'deptName', width: 110, render: (v) => v ?? '-' },
    {
      title: '角色',
      dataIndex: 'roleNames',
      ellipsis: true,
      render: (names: string[] | null) =>
        names?.length ? names.map((n) => <Tag key={n}>{n}</Tag>) : '-',
    },
    { title: '手机号', dataIndex: 'phone', width: 125, render: (v) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_, record) => (
        <Switch
          checked={record.status === 1}
          checkedChildren="启用"
          unCheckedChildren="停用"
          onChange={() => statusMutation.mutate(record)}
        />
      ),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'action',
      width: 250,
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => setRoleTarget(record)}>
            分配角色
          </Button>
          <Popconfirm
            title={record.status === 1 ? '应用级禁用该用户？（调认证中心，仅影响本系统）' : '应用级启用该用户？'}
            onConfirm={() => statusMutation.mutate(record)}
          >
            <Button type="link" size="small" danger={record.status === 1}>
              {record.status === 1 ? '禁用' : '启用'}
            </Button>
          </Popconfirm>
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
            同步认证中心用户
          </Button>
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
