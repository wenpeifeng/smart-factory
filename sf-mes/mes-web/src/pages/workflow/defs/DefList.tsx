import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
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
  pageDefs,
  getDef,
  createDef,
  updateDef,
  deleteDef,
  updateDefStatus,
  type WfDefinition,
  type WfDefinitionForm,
  type WfNode,
} from '@/api/workflow'
import { listAllRoles, pageUsers, type SysRole, type SysUser } from '@/api/system'
import { BIZ_TYPE_OPTIONS, BizTypeTag } from '@/pages/workflow/shared'

const { Title } = Typography

/** 节点表单值（Form.List 行） */
type NodeFormValue = Omit<WfNode, 'approverName' | 'id' | 'createTime'>

/** 定义表单值 */
type DefFormValue = Omit<WfDefinitionForm, 'id'>

/**
 * 流程定义页：列表（启停/删除/编辑）+ Drawer 主子表编辑（节点 Form.List）。
 * 节点审批人：类型选"角色"时下拉取角色列表（或签），选"指定用户"时取用户列表。
 */
export default function DefList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [keyword, setKeyword] = useState('')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  // 编辑抽屉（新增时 editing=null）
  const [editing, setEditing] = useState<WfDefinition | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<DefFormValue>()

  // 节点表内"审批人类型"切换需要即时读取，用受控缓存（Form.useWatch 监听整表太重）
  const [nodeTypes, setNodeTypes] = useState<Record<number, string>>({})

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['workflow', 'defs', keyword, pageNum, pageSize],
    queryFn: () => pageDefs({ pageNum, pageSize, keyword: keyword || undefined }),
  })

  // 审批人下拉数据源：角色（或签节点）+ 用户（指定节点）
  const { data: roles } = useQuery({ queryKey: ['system', 'roles', 'all'], queryFn: listAllRoles })
  const { data: users } = useQuery({
    queryKey: ['system', 'users', 'options'],
    queryFn: () => pageUsers({ pageNum: 1, pageSize: 100 }),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['workflow', 'defs'] })

  const saveMutation = useMutation({
    mutationFn: async (values: DefFormValue) => {
      const payload: WfDefinitionForm = { ...values, nodes: values.nodes ?? [] }
      if (editing) {
        await updateDef(editing.id, payload)
      } else {
        await createDef(payload)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功' : '新增成功')
      setOpen(false)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteDef,
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: number }) => updateDefStatus(id, status),
    onSuccess: () => {
      message.success('状态已更新')
      invalidate()
    },
  })

  /** 节点行默认值（追加在末尾，序号自增） */
  const appendNode = () => {
    const nodes = form.getFieldValue('nodes') as NodeFormValue[] | undefined
    const nextOrder = (nodes?.length ?? 0) + 1
    form.setFieldsValue({
      nodes: [...(nodes ?? []), { nodeOrder: nextOrder, nodeName: '', approverType: 'USER', approverId: undefined as unknown as number }],
    })
    setNodeTypes((prev) => ({ ...prev, [nextOrder]: 'USER' }))
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      status: 1,
      nodes: [{ nodeOrder: 1, nodeName: '', approverType: 'USER', approverId: undefined as unknown as number }],
    })
    setNodeTypes({ 1: 'USER' })
    setOpen(true)
  }

  const openEdit = async (def: WfDefinition) => {
    const detail = await getDef(def.id)
    setEditing(detail)
    form.resetFields()
    form.setFieldsValue({
      flowCode: detail.flowCode,
      flowName: detail.flowName,
      bizType: detail.bizType,
      status: detail.status,
      remark: detail.remark ?? undefined,
      nodes: (detail.nodes ?? []).map((n) => ({
        nodeOrder: n.nodeOrder,
        nodeName: n.nodeName,
        approverType: n.approverType,
        approverId: n.approverId,
        conditionExpr: n.conditionExpr ?? undefined,
      })),
    })
    setNodeTypes(Object.fromEntries((detail.nodes ?? []).map((n) => [n.nodeOrder, n.approverType])))
    setOpen(true)
  }

  const columns: ColumnsType<WfDefinition> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '流程编码', dataIndex: 'flowCode', width: 150 },
    { title: '流程名称', dataIndex: 'flowName', width: 180, ellipsis: true },
    { title: '适用单据', dataIndex: 'bizType', width: 120, render: (v) => <BizTypeTag bizType={v} /> },
    { title: '节点数', dataIndex: 'nodeCount', width: 85, render: (v) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_, record) => (
        <Switch
          checked={record.status === 1}
          checkedChildren="启用"
          unCheckedChildren="停用"
          onChange={() => statusMutation.mutate({ id: record.id, status: record.status === 1 ? 0 : 1 })}
        />
      ),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v) => v ?? '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'action',
      width: 130,
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该流程定义？" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  /** 审批人下拉选项（按节点审批人类型切换角色/用户） */
  const approverOptions = (approverType: string | undefined) =>
    approverType === 'APPROVER'
      ? (roles ?? []).map((r: SysRole) => ({ label: `角色：${r.name}`, value: r.id }))
      : (users?.records ?? []).map((u: SysUser) => ({ label: u.nickname, value: u.id }))

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        流程定义
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="编码 / 名称模糊搜索"
            style={{ width: 240 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setKeyword(v)
            }}
          />
          <Button type="primary" onClick={openCreate}>
            新增流程
          </Button>
        </Space>

        <Table<WfDefinition>
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

      {/* 新增/修改抽屉：主表 + 节点子表 */}
      <Drawer
        title={editing ? `修改流程 - ${editing.flowCode}` : '新增流程'}
        open={open}
        onClose={() => setOpen(false)}
        width={760}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setOpen(false)}>取消</Button>
            <Button type="primary" loading={saveMutation.isPending} onClick={() => form.submit()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="flowCode"
              label="流程编码"
              rules={[
                { required: true, message: '请输入流程编码' },
                { pattern: /^[A-Za-z][A-Za-z0-9_-]*$/, message: '字母开头，仅字母/数字/下划线/中划线' },
              ]}
              style={{ width: 200 }}
            >
              <Input placeholder="如 FLOW-PO-001" maxLength={40} />
            </Form.Item>
            <Form.Item
              name="flowName"
              label="流程名称"
              rules={[{ required: true, message: '请输入流程名称' }]}
              style={{ width: 200 }}
            >
              <Input placeholder="如 采购订单审批流" maxLength={60} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="bizType"
              label="适用单据"
              rules={[{ required: true, message: '请选择适用单据' }]}
              style={{ width: 200 }}
            >
              <Select placeholder="选择单据类型" options={BIZ_TYPE_OPTIONS} />
            </Form.Item>
            <Form.Item name="status" label="状态" initialValue={1} style={{ width: 200 }}>
              <Select
                options={[
                  { label: '启用', value: 1 },
                  { label: '停用', value: 0 },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input placeholder="备注" maxLength={200} />
          </Form.Item>

          {/* 节点子表：序号串行流转，条件不满足自动跳过 */}
          <Form.Item label="审批节点" required style={{ marginBottom: 0 }}>
            <Form.List name="nodes">
              {(fields, { remove }) => (
                <>
                  {fields.map((field) => (
                    <Space key={field.key} align="baseline" style={{ display: 'flex', marginBottom: 4 }}>
                      <Form.Item
                        name={[field.name, 'nodeOrder']}
                        rules={[{ required: true, message: '序号' }]}
                        style={{ width: 70 }}
                      >
                        <InputNumber min={1} precision={0} placeholder="序号" />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'nodeName']}
                        rules={[{ required: true, message: '请输入节点名称' }]}
                        style={{ width: 140 }}
                      >
                        <Input placeholder="节点名称" maxLength={60} />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'approverType']}
                        rules={[{ required: true }]}
                        style={{ width: 110 }}
                      >
                        <Select
                          options={[
                            { label: '指定用户', value: 'USER' },
                            { label: '按角色(或签)', value: 'APPROVER' },
                          ]}
                          onChange={(v) => {
                            // 切换类型时清空已选审批人，避免提交角色ID/用户ID混用
                            const nodes = form.getFieldValue('nodes') as NodeFormValue[] | undefined
                            if (nodes) {
                              nodes[field.name].approverId = undefined as unknown as number
                              form.setFieldsValue({ nodes: [...nodes] })
                            }
                            setNodeTypes((prev) => ({ ...prev, [field.name]: v }))
                          }}
                        />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'approverId']}
                        rules={[{ required: true, message: '请选择审批人' }]}
                        style={{ width: 180 }}
                      >
                        <Select
                          showSearch
                          optionFilterProp="label"
                          placeholder={
                            nodeTypes[field.name] === 'APPROVER' ? '选择角色' : '选择用户'
                          }
                          options={approverOptions(nodeTypes[field.name])}
                          allowClear
                        />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'conditionExpr']}
                        style={{ width: 150 }}
                        tooltip="满足条件才走本节点，否则跳过；如 amount>10000"
                      >
                        <Input placeholder="条件(可空)" maxLength={200} />
                      </Form.Item>
                      <Button
                        type="link"
                        size="small"
                        danger
                        onClick={() => remove(field.name)}
                        disabled={fields.length <= 1}
                      >
                        删除
                      </Button>
                    </Space>
                  ))}
                  <Button type="dashed" onClick={appendNode} style={{ width: '100%' }}>
                    + 添加节点
                  </Button>
                </>
              )}
            </Form.List>
          </Form.Item>
          <Space size={4} style={{ marginTop: 8 }}>
            <Tag color="blue">说明</Tag>
            <span style={{ color: '#888', fontSize: 12 }}>
              节点按序号从小到大串行；条件表达式形如 amount&gt;10000（变量取自发起时的业务变量）；
              角色节点为"或签"，角色下任一成员处理即推进到下一节点。
            </span>
          </Space>
        </Form>
      </Drawer>
    </div>
  )
}
