import { useState } from 'react'
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
  getDeptTree,
  createDept,
  updateDept,
  deleteDept,
  type SysDept,
  type SysDeptForm,
} from '@/api/system'

const { Title } = Typography

/** 树拍平成表格行（带层级信息） */
interface DeptRow extends SysDept {
  level: number
}

function flatten(nodes: SysDept[], level = 0): DeptRow[] {
  return nodes.flatMap((n) => [{ ...n, level }, ...flatten(n.children, level + 1)])
}

/** 部门 VO → 表单值 */
function toDeptForm(d: SysDept): SysDeptForm {
  return {
    id: d.id,
    parentId: d.parentId,
    name: d.name,
    sort: d.sort,
    leader: d.leader ?? undefined,
    phone: d.phone ?? undefined,
    status: d.status,
  }
}

/**
 * 部门管理页：树形 CRUD（parent_id 自关联，支持无限层级）。
 */
export default function DeptList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [keyword, setKeyword] = useState('')
  const [editing, setEditing] = useState<SysDept | null>(null)
  const [parentOf, setParentOf] = useState<SysDept | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<SysDeptForm>()

  const { data: tree, isLoading, isFetching } = useQuery({
    queryKey: ['system', 'depts', 'tree', keyword],
    queryFn: () => getDeptTree(keyword || undefined),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['system', 'depts'] })

  const saveMutation = useMutation({
    mutationFn: async (values: SysDeptForm) => {
      if (editing) {
        await updateDept(editing.id, values)
      } else {
        await createDept(values)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功')
      setOpen(false)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteDept,
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const openCreate = (parent: SysDept | null) => {
    setEditing(null)
    setParentOf(parent)
    form.resetFields()
    form.setFieldsValue({ parentId: parent?.id ?? 0, sort: 0, status: 1 })
    setOpen(true)
  }

  const openEdit = (dept: SysDept) => {
    setEditing(dept)
    setParentOf(null)
    form.resetFields()
    form.setFieldsValue(toDeptForm(dept))
    setOpen(true)
  }

  /** 上级选择：全部部门（编辑时排除自身子树，防环由后端兜底） */
  const parentOptions = (() => {
    const options: { label: string; value: number; disabled?: boolean }[] = [{ label: '根节点', value: 0 }]
    const walk = (nodes: SysDept[], depth: number, inDisabled: boolean) => {
      nodes.forEach((n) => {
        const disabled = inDisabled || (editing != null && n.id === editing.id)
        options.push({ label: `${'— '.repeat(depth)}${n.name}`, value: n.id, disabled })
        walk(n.children, depth + 1, disabled)
      })
    }
    walk(tree ?? [], 0, false)
    return options
  })()

  const columns: ColumnsType<DeptRow> = [
    {
      title: '部门名称',
      dataIndex: 'name',
      render: (name: string, record) => (
        <span style={{ paddingLeft: record.level * 20 }}>
          {record.level > 0 && <span style={{ color: '#c0c4cc', marginRight: 4 }}>└</span>}
          {name}
        </span>
      ),
    },
    { title: '负责人', dataIndex: 'leader', width: 120, render: (v) => v ?? '-' },
    { title: '联系电话', dataIndex: 'phone', width: 140, render: (v) => v ?? '-' },
    { title: '排序', dataIndex: 'sort', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) =>
        v === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>,
    },
    { title: '创建时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => openCreate(record)}>
            新增下级
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该部门？" onConfirm={() => deleteMutation.mutate(record.id)}>
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
        部门管理
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="部门名称搜索"
            style={{ width: 240 }}
            allowClear
            onSearch={setKeyword}
          />
          <Button type="primary" onClick={() => openCreate(null)}>
            新增部门
          </Button>
        </Space>

        <Table<DeptRow>
          rowKey="id"
          columns={columns}
          dataSource={flatten(tree ?? [])}
          loading={isLoading || isFetching}
          pagination={false}
          size="middle"
        />
      </Card>

      {/* 新增 / 修改弹窗 */}
      <Modal
        title={
          editing ? `修改部门 - ${editing.name}` : `新增部门${parentOf ? `（上级：${parentOf.name}）` : ''}`
        }
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Form.Item name="parentId" label="上级部门" rules={[{ required: true }]}>
            <Select options={parentOptions} disabled={!!editing} />
          </Form.Item>
          <Form.Item name="name" label="部门名称" rules={[{ required: true, message: '请输入部门名称' }]}>
            <Input placeholder="如：技术部" maxLength={50} />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="leader" label="负责人" style={{ width: 180 }}>
              <Input placeholder="负责人" maxLength={30} />
            </Form.Item>
            <Form.Item
              name="phone"
              label="联系电话"
              style={{ width: 180 }}
              rules={[{ pattern: /^[0-9+()-]{5,20}$/, message: '电话格式不正确' }]}
            >
              <Input placeholder="联系电话" maxLength={20} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="sort" label="排序" initialValue={0}>
              <InputNumber min={0} max={9999} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="status" label="状态" initialValue={1}>
              <Select
                options={[
                  { label: '启用', value: 1 },
                  { label: '停用', value: 0 },
                ]}
                style={{ width: 120 }}
              />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  )
}
