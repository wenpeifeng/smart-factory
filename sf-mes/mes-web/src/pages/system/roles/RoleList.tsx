import { useEffect, useState, type Key } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tree,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { DataNode } from 'antd/es/tree'
import {
  pageRoles,
  createRole,
  updateRole,
  deleteRole,
  getRoleMenuIds,
  assignRoleMenus,
  getMenuTree,
  type SysRole,
  type SysRoleForm,
  type SysMenu,
} from '@/api/system'

const { Title } = Typography

/** 菜单树 → AntD Tree 数据（title 拼上类型标签，便于勾选辨识） */
function menuToTreeData(nodes: SysMenu[]): DataNode[] {
  return nodes.map((n) => ({
    key: n.id,
    title:
      n.type === 3 ? `${n.name}（${n.perms ?? '按钮'}）` : `${n.name}${n.type === 1 ? '（目录）' : ''}`,
    children: n.children.length ? menuToTreeData(n.children) : undefined,
  }))
}

/** 树拍平取全部 key（展开用） */
function allKeys(nodes: SysMenu[]): number[] {
  return nodes.flatMap((n) => [n.id, ...allKeys(n.children)])
}

/** 角色 VO → 表单值 */
function toRoleForm(r: SysRole): SysRoleForm {
  return {
    id: r.id,
    name: r.name,
    code: r.code,
    sort: r.sort,
    status: r.status,
    remark: r.remark ?? undefined,
  }
}

/**
 * 角色管理页：分页 CRUD + 按角色分配菜单（目录/菜单/按钮三级树勾选）。
 */
export default function RoleList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [keyword, setKeyword] = useState('')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [editing, setEditing] = useState<SysRole | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<SysRoleForm>()

  // 分配菜单弹窗
  const [menuTarget, setMenuTarget] = useState<SysRole | null>(null)
  const [checkedKeys, setCheckedKeys] = useState<{ checked: Key[]; halfChecked: Key[] }>({
    checked: [],
    halfChecked: [],
  })

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['system', 'roles', keyword, pageNum, pageSize],
    queryFn: () => pageRoles({ pageNum, pageSize, keyword: keyword || undefined }),
  })
  const { data: menuTree } = useQuery({ queryKey: ['system', 'menus', 'tree'], queryFn: () => getMenuTree() })

  // 打开分配菜单弹窗时回显已勾选节点（叶子 + 已勾父节点）
  useEffect(() => {
    if (menuTarget) {
      getRoleMenuIds(menuTarget.id).then((ids) =>
        setCheckedKeys({ checked: ids as Key[], halfChecked: [] }),
      )
    } else {
      setCheckedKeys({ checked: [], halfChecked: [] })
    }
  }, [menuTarget])

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['system', 'roles'] })

  const saveMutation = useMutation({
    mutationFn: async (values: SysRoleForm) => {
      if (editing) {
        await updateRole(editing.id, values)
      } else {
        await createRole(values)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功')
      setOpen(false)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteRole,
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const menuMutation = useMutation({
    mutationFn: ({ id, menuIds }: { id: number; menuIds: number[] }) => assignRoleMenus(id, menuIds),
    onSuccess: () => {
      message.success('菜单已分配')
      setMenuTarget(null)
    },
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ status: 1, sort: 0 })
    setOpen(true)
  }

  const openEdit = (role: SysRole) => {
    setEditing(role)
    form.resetFields()
    form.setFieldsValue(toRoleForm(role))
    setOpen(true)
  }

  const columns: ColumnsType<SysRole> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '角色名称', dataIndex: 'name', width: 160 },
    {
      title: '角色编码',
      dataIndex: 'code',
      width: 160,
      render: (code: string) =>
        code === 'super_admin' ? <Tag color="gold">{code}</Tag> : <Tag>{code}</Tag>,
    },
    { title: '排序', dataIndex: 'sort', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) =>
        v === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>,
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v) => v ?? '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'action',
      width: 210,
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button type="link" size="small" onClick={() => setMenuTarget(record)} disabled={record.code === 'super_admin'}>
            分配菜单
          </Button>
          {record.code === 'super_admin' ? (
            <Button type="link" size="small" disabled title="内置角色不可删除">
              删除
            </Button>
          ) : (
            <Popconfirm title="确认删除该角色？" onConfirm={() => deleteMutation.mutate(record.id)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        角色管理
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="名称 / 编码模糊搜索"
            style={{ width: 240 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setKeyword(v)
            }}
          />
          <Button type="primary" onClick={openCreate}>
            新增角色
          </Button>
        </Space>

        <Table<SysRole>
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
        title={editing ? `修改角色 - ${editing.name}` : '新增角色'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Form.Item
            name="name"
            label="角色名称"
            rules={[{ required: true, message: '请输入角色名称' }]}
          >
            <Input placeholder="如：采购经理" maxLength={30} />
          </Form.Item>
          <Form.Item
            name="code"
            label="角色编码"
            rules={[
              { required: true, message: '请输入角色编码' },
              { pattern: /^[A-Za-z][A-Za-z0-9_]{1,49}$/, message: '字母开头的字母/数字/下划线，2-50 位' },
            ]}
            extra="全局唯一；super_admin 为内置超管"
          >
            <Input placeholder="如：purchase_manager" maxLength={50} disabled={editing?.code === 'super_admin'} />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="sort" label="排序" initialValue={0} style={{ width: 120 }}>
              <InputNumber min={0} max={9999} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="status" label="状态" initialValue={1} style={{ width: 160 }}>
              <Select
                options={[
                  { label: '启用', value: 1 },
                  { label: '停用', value: 0 },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea placeholder="备注" maxLength={200} rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 分配菜单弹窗 */}
      <Modal
        title={`分配菜单 - ${menuTarget?.name ?? ''}`}
        open={!!menuTarget}
        onCancel={() => setMenuTarget(null)}
        onOk={() =>
          menuTarget &&
          menuMutation.mutate({
            id: menuTarget.id,
            // 半选的父节点也要提交，否则目录勾选状态丢失
            menuIds: [...checkedKeys.checked, ...checkedKeys.halfChecked].map(Number),
          })
        }
        confirmLoading={menuMutation.isPending}
        destroyOnClose
        width={520}
      >
        <Tree
          checkable
          defaultExpandedKeys={allKeys(menuTree ?? [])}
          treeData={menuToTreeData(menuTree ?? [])}
          checkedKeys={checkedKeys}
          onCheck={(keys, info) =>
            setCheckedKeys({
              checked: keys as Key[],
              halfChecked: (info.halfCheckedKeys ?? []) as Key[],
            })
          }
        />
      </Modal>
    </div>
  )
}
