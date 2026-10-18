import { useEffect, useState } from 'react'
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
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  getMenuTree,
  createMenu,
  updateMenu,
  deleteMenu,
  type SysMenu,
  type SysMenuForm,
} from '@/api/system'

const { Title } = Typography

/** 菜单类型元数据（三级：目录/菜单/按钮） */
const TYPE_META: Record<number, { label: string; color: string }> = {
  1: { label: '目录', color: 'geekblue' },
  2: { label: '菜单', color: 'blue' },
  3: { label: '按钮', color: 'orange' },
}

/** 菜单表单字段随类型联动展示的 key 集合 */
function fieldsForType(type: number): ('path' | 'component' | 'perms')[] {
  if (type === 1) return ['path']
  if (type === 2) return ['path', 'component']
  return ['perms']
}

/** 树拍平成表格行（带层级缩进的 name 用于树表格展示） */
interface MenuRow extends SysMenu {
  level: number
}

function flatten(nodes: SysMenu[], level = 0): MenuRow[] {
  return nodes.flatMap((n) => [{ ...n, level }, ...flatten(n.children, level + 1)])
}

/** 菜单 VO → 表单值 */
function toMenuForm(m: SysMenu): SysMenuForm {
  return {
    id: m.id,
    parentId: m.parentId,
    name: m.name,
    type: m.type,
    path: m.path ?? undefined,
    component: m.component ?? undefined,
    perms: m.perms ?? undefined,
    icon: m.icon ?? undefined,
    sort: m.sort,
    visible: m.visible,
    status: m.status,
  }
}

/**
 * 菜单管理页：三级树（目录/菜单/按钮）CRUD。
 * 树形展示用拍平 + 缩进（比 AntD 树表格更直观可控）。
 */
export default function MenuList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [keyword, setKeyword] = useState('')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [editing, setEditing] = useState<SysMenu | null>(null)
  const [parentOf, setParentOf] = useState<SysMenu | null>(null) // 新增子节点时的父节点
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<SysMenuForm>()

  const { data: tree, isLoading, isFetching } = useQuery({
    queryKey: ['system', 'menus', 'tree', keyword],
    queryFn: () => getMenuTree(keyword || undefined),
    staleTime: 5 * 60 * 1000,
    placeholderData: (previousData) => previousData,
  })

  const rows = flatten(tree ?? [])
  const lastPage = Math.max(1, Math.ceil(rows.length / pageSize))
  const pageRows = rows.slice((pageNum - 1) * pageSize, pageNum * pageSize)

  useEffect(() => {
    if (!isLoading && pageNum > lastPage) setPageNum(lastPage)
  }, [isLoading, lastPage, pageNum])

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['system', 'menus'] })

  const saveMutation = useMutation({
    mutationFn: async (values: SysMenuForm) => {
      if (editing) {
        await updateMenu(editing.id, values)
      } else {
        await createMenu(values)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功')
      setOpen(false)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteMenu,
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const openCreate = (parent: SysMenu | null) => {
    setEditing(null)
    setParentOf(parent)
    form.resetFields()
    // 父节点为菜单时默认新增按钮，否则默认新增菜单
    const defaultType = parent?.type === 2 ? 3 : parent?.type === 1 ? 2 : 1
    form.setFieldsValue({ parentId: parent?.id ?? 0, type: defaultType, sort: 0, visible: 1, status: 1 })
    setOpen(true)
  }

  const openEdit = (menu: SysMenu) => {
    setEditing(menu)
    setParentOf(null)
    form.resetFields()
    form.setFieldsValue(toMenuForm(menu))
    setOpen(true)
  }

  const selectedType = Form.useWatch('type', form) ?? 2
  const visibleFields = fieldsForType(selectedType)

  /** 上级选择数据：目录 + 菜单（按钮可挂菜单下）；编辑时排除自身子树 */
  const parentOptions = (() => {
    const options: { title: string; value: number; disabled?: boolean }[] = [{ title: '根节点', value: 0 }]
    const walk = (nodes: SysMenu[], disabled: boolean) => {
      nodes.forEach((n) => {
        const selfOrDescendant = editing != null && n.id === editing.id
        const isDisabled = disabled || selfOrDescendant
        if (n.type !== 3) {
          options.push({ title: `${'　'.repeat(0)}${n.name}`, value: n.id, disabled: isDisabled })
        }
        walk(n.children, isDisabled)
      })
    }
    walk(tree ?? [], false)
    return options
  })()

  const columns: ColumnsType<MenuRow> = [
    {
      title: '名称',
      dataIndex: 'name',
      render: (name: string, record) => (
        <span style={{ paddingLeft: record.level * 20 }}>
          {record.level > 0 && <span style={{ color: '#c0c4cc', marginRight: 4 }}>└</span>}
          {name}
        </span>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 80,
      render: (t: number) => <Tag color={TYPE_META[t]?.color}>{TYPE_META[t]?.label}</Tag>,
    },
    { title: '路由地址', dataIndex: 'path', width: 180, render: (v) => v ?? '-' },
    { title: '组件路径', dataIndex: 'component', width: 220, ellipsis: true, render: (v) => v ?? '-' },
    { title: '权限标识', dataIndex: 'perms', width: 180, render: (v) => v ?? '-' },
    { title: '排序', dataIndex: 'sort', width: 70 },
    {
      title: '可见',
      dataIndex: 'visible',
      width: 70,
      render: (v: number) => (v === 1 ? '显示' : '隐藏'),
    },
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
      width: 200,
      render: (_, record) => (
        <Space size={4}>
          {record.type !== 3 && (
            <Button type="link" size="small" onClick={() => openCreate(record)}>
              新增下级
            </Button>
          )}
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该节点？" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        菜单管理
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="名称 / 权限标识搜索（自动保留祖先链）"
            style={{ width: 280 }}
            allowClear
            onSearch={(value) => { setPageNum(1); setKeyword(value) }}
          />
          <Button type="primary" onClick={() => openCreate(null)}>
            新增目录/菜单
          </Button>
        </Space>

        <Table<MenuRow>
          rowKey="id"
          columns={columns}
          dataSource={pageRows}
          loading={{ spinning: isLoading || isFetching, tip: '正在加载菜单结构…' }}
          pagination={{
            current: pageNum,
            pageSize,
            total: rows.length,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50],
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, size) => { setPageNum(page); setPageSize(size) },
          }}
          size="middle"
          scroll={{ x: 1250 }}
        />
      </Card>

      {/* 新增 / 修改弹窗 */}
      <Modal
        title={
          editing
            ? `修改菜单 - ${editing.name}`
            : `新增节点${parentOf ? `（上级：${parentOf.name}）` : ''}`
        }
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
        width={560}
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="parentId" label="上级节点" rules={[{ required: true }]} style={{ width: 240 }}>
              <Select
                options={parentOptions.map((o) => ({ label: o.title, value: o.value, disabled: o.disabled }))}
                disabled={!!editing}
              />
            </Form.Item>
            <Form.Item name="type" label="类型" rules={[{ required: true }]} style={{ width: 120 }}>
              <Select
                disabled={!!editing}
                options={[
                  { label: '目录', value: 1 },
                  { label: '菜单', value: 2 },
                  { label: '按钮', value: 3 },
                ]}
              />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]} style={{ width: 240 }}>
              <Input placeholder="如：用户管理" maxLength={50} />
            </Form.Item>
            <Form.Item name="sort" label="排序" initialValue={0} style={{ width: 120 }}>
              <InputNumber min={0} max={9999} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          {visibleFields.includes('path') && (
            <Form.Item
              name="path"
              label="路由地址"
              rules={[{ required: true, message: '请输入路由地址' }]}
              extra="完整路径不带斜杠，如 system/users"
            >
              <Input placeholder="system/users" maxLength={200} />
            </Form.Item>
          )}
          {visibleFields.includes('component') && (
            <Form.Item
              name="component"
              label="前端组件路径"
              rules={[{ required: true, message: '请输入组件路径' }]}
              extra="相对 pages/ 的路径，如 system/users/UserList"
            >
              <Input placeholder="system/users/UserList" maxLength={200} />
            </Form.Item>
          )}
          {visibleFields.includes('perms') && (
            <Form.Item
              name="perms"
              label="权限标识"
              rules={[{ required: true, message: '请输入权限标识' }]}
              extra="如 system:user:add"
            >
              <Input placeholder="system:user:add" maxLength={100} />
            </Form.Item>
          )}
          <Space size={16} style={{ display: 'flex' }}>
            {selectedType !== 3 && (
              <Form.Item name="icon" label="图标名" style={{ width: 180 }}>
                <Input placeholder="如 TeamOutlined" maxLength={50} />
              </Form.Item>
            )}
            <Form.Item name="visible" label="是否显示" initialValue={1} style={{ width: 130 }}>
              <Select
                options={[
                  { label: '显示', value: 1 },
                  { label: '隐藏', value: 0 },
                ]}
              />
            </Form.Item>
            <Form.Item name="status" label="状态" initialValue={1} style={{ width: 130 }}>
              <Select
                options={[
                  { label: '启用', value: 1 },
                  { label: '停用', value: 0 },
                ]}
              />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  )
}
