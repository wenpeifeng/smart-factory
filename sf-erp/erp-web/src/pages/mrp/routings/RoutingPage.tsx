import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import PageTable from '@/components/PageTable'
import PageHeader from '@/components/PageHeader'
import StatusTag, { type StatusSemantic } from '@/components/StatusTag'
import { useDebounce } from '@/hooks/useDebounce'
import { useSubmitting } from '@/hooks/useSubmitting'
import {
  operationApi,
  routingApi,
  workCenterApi,
  type OperationForm,
  type OperationRow,
  type RoutingForm,
  type RoutingRow,
  type RoutingStepRow,
  type RoutingTree,
  type WorkCenterForm,
  type WorkCenterRow,
} from '@/api/mrp'
import MaterialSelect from '../components/MaterialSelect'

const { Text } = Typography

/** 工艺路线状态 -> 中文 + 语义 */
const ROUTING_STATUS: Record<string, { label: string; semantic: StatusSemantic }> = {
  DRAFT: { label: '草稿', semantic: 'default' },
  ACTIVE: { label: '已审核', semantic: 'success' },
  DISABLED: { label: '停用', semantic: 'default' },
}

/** 启用/停用状态渲染 */
const renderEnableStatus = (v: string) => (
  <StatusTag value={v === 'ENABLED' ? '启用' : '停用'} />
)

/** 编辑态工艺路线步骤行（前端临时 key，序号自动连续编号） */
interface EditableStep {
  key: string
  operationId?: number
  workCenterId?: number
  unitMinutes?: number
  reportFlag: boolean
  remark?: string
}

/** 步骤编辑列（新建/编辑工艺路线弹窗内的可编辑子表） */
function StepEditor({
  steps,
  onChange,
  operationOptions,
  workCenterOptions,
}: {
  steps: EditableStep[]
  onChange: (next: EditableStep[]) => void
  operationOptions: { value: number; label: string }[]
  workCenterOptions: { value: number; label: string }[]
}) {
  const update = (key: string, patch: Partial<EditableStep>) =>
    onChange(steps.map((s) => (s.key === key ? { ...s, ...patch } : s)))

  return (
    <>
      <Table<EditableStep>
        rowKey="key"
        size="small"
        dataSource={steps}
        pagination={false}
        columns={[
          {
            title: '序号',
            key: 'seqNo',
            width: 56,
            render: (_, _row, idx) => idx + 1,
          },
          {
            title: '工序',
            dataIndex: 'operationId',
            render: (_, row) => (
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="选择工序"
                style={{ width: '100%', minWidth: 160 }}
                value={row.operationId}
                options={operationOptions}
                onChange={(v) => update(row.key, { operationId: v })}
              />
            ),
          },
          {
            title: '工作中心（空=工序默认）',
            dataIndex: 'workCenterId',
            width: 190,
            render: (_, row) => (
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="默认"
                style={{ width: '100%' }}
                value={row.workCenterId}
                options={workCenterOptions}
                onChange={(v) => update(row.key, { workCenterId: v })}
              />
            ),
          },
          {
            title: '单件工时(分钟)',
            dataIndex: 'unitMinutes',
            width: 130,
            render: (_, row) => (
              <InputNumber
                min={0}
                step={0.5}
                placeholder="默认"
                style={{ width: '100%' }}
                value={row.unitMinutes}
                onChange={(v) => update(row.key, { unitMinutes: v ?? undefined })}
              />
            ),
          },
          {
            title: '报工点',
            dataIndex: 'reportFlag',
            width: 80,
            render: (_, row) => (
              <Switch
                size="small"
                checked={row.reportFlag}
                onChange={(v) => update(row.key, { reportFlag: v })}
              />
            ),
          },
          {
            title: '备注',
            dataIndex: 'remark',
            width: 140,
            render: (_, row) => (
              <Input
                placeholder="可选"
                value={row.remark}
                onChange={(e) => update(row.key, { remark: e.target.value })}
              />
            ),
          },
          {
            title: '操作',
            key: 'op',
            width: 70,
            render: (_, row) => (
              <Button
                size="small"
                danger
                disabled={steps.length <= 1}
                onClick={() => onChange(steps.filter((s) => s.key !== row.key))}
              >
                移除
              </Button>
            ),
          },
        ]}
      />
      <Button
        type="dashed"
        block
        style={{ marginTop: 8 }}
        icon={<PlusOutlined />}
        onClick={() =>
          onChange([...steps, { key: `step-${Date.now()}`, reportFlag: true }])
        }
      >
        添加工序步骤
      </Button>
    </>
  )
}

/** 工艺路线 Tab：列表 + 步骤子表编辑 + 复制新版本 + 审核/停用 + 工序序列查看 */
function RoutingTab() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebounce(keyword)
  const [status, setStatus] = useState<string | undefined>()

  // 编辑弹窗（主子表）
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<{ materialId?: number; version: string; remark: string }>({
    version: '',
    remark: '',
  })
  const [steps, setSteps] = useState<EditableStep[]>([])

  // 工序序列 / 复制新版本弹窗
  const [treeOpen, setTreeOpen] = useState(false)
  const [treeId, setTreeId] = useState<number | null>(null)
  const [copyOpen, setCopyOpen] = useState(false)
  const [copyId, setCopyId] = useState<number | null>(null)
  const [newVersion, setNewVersion] = useState('')

  const { data: operations } = useQuery({
    queryKey: ['mrp', 'operations', 'all'],
    queryFn: operationApi.listAll,
  })
  const { data: workCenters } = useQuery({
    queryKey: ['mrp', 'work-centers', 'all'],
    queryFn: workCenterApi.listAll,
  })
  const { data: treeData, isLoading: treeLoading } = useQuery({
    queryKey: ['mrp', 'routing-tree', treeId],
    queryFn: () => routingApi.tree(treeId as number),
    enabled: treeOpen && treeId != null,
  })

  const operationOptions = (operations ?? []).map((o) => ({
    value: o.id,
    label: `${o.code} | ${o.name}`,
    defaultWorkCenterId: o.defaultWorkCenterId,
    unitMinutes: o.unitMinutes,
  }))
  const workCenterOptions = (workCenters ?? []).map((w) => ({
    value: w.id,
    label: `${w.code} | ${w.name}`,
  }))

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['mrp', 'routings'] })

  const saveSubmit = useSubmitting(
    async (payload: RoutingForm) => {
      if (editingId) await routingApi.update(editingId, payload)
      else await routingApi.create(payload)
    },
    {
      successText: editingId ? '工艺路线已保存' : '工艺路线已创建（草稿）',
      onSuccess: () => {
        setEditorOpen(false)
        invalidate()
      },
    },
  )

  const actionSubmit = useSubmitting(
    async (action: 'audit' | 'disable' | 'delete', id: number) => {
      if (action === 'audit') await routingApi.audit(id)
      else if (action === 'disable') await routingApi.disable(id)
      else await routingApi.remove(id)
    },
    {
      successText: '操作成功',
      onSuccess: () => invalidate(),
    },
  )

  const copySubmit = useSubmitting(
    async () => {
      await routingApi.copy(copyId as number, newVersion.trim())
    },
    {
      successText: '已复制为新版本（草稿）',
      onSuccess: () => {
        setCopyOpen(false)
        invalidate()
      },
    },
  )

  /** 打开编辑弹窗（编辑时先拉详情回填步骤子表） */
  const openEditor = async (row?: RoutingRow) => {
    if (row) {
      const detail = await routingApi.detail(row.id)
      setEditingId(row.id)
      setForm({
        materialId: detail.materialId,
        version: detail.version,
        remark: detail.remark ?? '',
      })
      setSteps(
        (detail.steps ?? []).map((s: RoutingStepRow) => ({
          key: `step-${s.id}`,
          operationId: s.operationId,
          workCenterId: s.workCenterId ?? undefined,
          unitMinutes: s.unitMinutes ?? undefined,
          reportFlag: s.reportFlag === 1,
          remark: s.remark ?? undefined,
        })),
      )
    } else {
      setEditingId(null)
      setForm({ version: '', remark: '' })
      setSteps([{ key: `step-${Date.now()}`, reportFlag: true }])
    }
    setEditorOpen(true)
  }

  const submitEditor = () => {
    if (!form.materialId) return message.warning('请选择成品物料')
    if (!form.version.trim()) return message.warning('请填写版本号')
    if (steps.length === 0) return message.warning('请至少添加一个工序步骤')
    if (steps.some((s) => !s.operationId)) return message.warning('存在未选择工序的步骤行')
    saveSubmit.run({
      materialId: form.materialId,
      version: form.version.trim(),
      remark: form.remark || undefined,
      steps: steps.map((s, idx) => ({
        seqNo: idx + 1,
        operationId: s.operationId as number,
        workCenterId: s.workCenterId,
        unitMinutes: s.unitMinutes,
        reportFlag: s.reportFlag ? 1 : 0,
        remark: s.remark || undefined,
      })),
    })
  }

  const columns: ColumnsType<RoutingRow> = [
    { title: 'ID', dataIndex: 'id', width: 64 },
    { title: '成品编码', dataIndex: 'materialCode', width: 130 },
    { title: '成品名称', dataIndex: 'materialName', ellipsis: true },
    { title: '单位', dataIndex: 'unit', width: 64 },
    { title: '版本', dataIndex: 'version', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 88,
      render: (v: string) => {
        const s = ROUTING_STATUS[v]
        return s ? <StatusTag value={s.label} semantic={s.semantic} /> : v
      },
    },
    { title: '步骤数', dataIndex: 'stepCount', width: 76, align: 'right' },
    { title: '创建时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'actions',
      width: 320,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button
            size="small"
            disabled={row.status !== 'DRAFT'}
            onClick={() => openEditor(row)}
          >
            编辑
          </Button>
          {row.status === 'DRAFT' && (
            <Popconfirm
              title="审核后该路线生效，生产订单下达时将按其生成工序任务"
              onConfirm={() => actionSubmit.run('audit', row.id)}
            >
              <Button size="small" type="primary" ghost>
                审核
              </Button>
            </Popconfirm>
          )}
          {row.status === 'ACTIVE' && (
            <Popconfirm
              title="停用后生产订单下达不再使用该路线"
              onConfirm={() => actionSubmit.run('disable', row.id)}
            >
              <Button size="small">停用</Button>
            </Popconfirm>
          )}
          <Button
            size="small"
            onClick={() => {
              setTreeId(row.id)
              setTreeOpen(true)
            }}
          >
            工序序列
          </Button>
          <Button
            size="small"
            onClick={() => {
              setCopyId(row.id)
              setNewVersion('')
              setCopyOpen(true)
            }}
          >
            复制新版
          </Button>
          {row.status !== 'ACTIVE' && (
            <Popconfirm title="确认删除该工艺路线？" onConfirm={() => actionSubmit.run('delete', row.id)}>
              <Button size="small" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <PageTable<RoutingRow>
        url="/mrp/routings"
        queryKey={['mrp', 'routings', debouncedKeyword, status]}
        params={{ keyword: debouncedKeyword || undefined, status }}
        columns={columns}
        toolbar={
          <>
            <Input.Search
              placeholder="成品物料编码 / 名称搜索"
              style={{ width: 240 }}
              allowClear
              onSearch={(v) => setKeyword(v)}
            />
            <Select
              placeholder="状态筛选"
              allowClear
              style={{ width: 130 }}
              value={status}
              onChange={(v) => setStatus(v)}
              options={[
                { value: 'DRAFT', label: '草稿' },
                { value: 'ACTIVE', label: '已审核' },
                { value: 'DISABLED', label: '停用' },
              ]}
            />
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor()}>
              新建工艺路线
            </Button>
          </>
        }
      />

      {/* 新建/编辑弹窗（主子表：头 + 步骤整体提交） */}
      <Modal
        title={editingId ? `编辑工艺路线 #${editingId}` : '新建工艺路线'}
        open={editorOpen}
        onCancel={() => setEditorOpen(false)}
        onOk={submitEditor}
        confirmLoading={saveSubmit.loading}
        width={920}
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Space wrap>
            <div style={{ width: 320 }}>
              <div style={{ marginBottom: 4 }}>成品物料</div>
              <MaterialSelect
                value={form.materialId}
                onChange={(v) => setForm((f) => ({ ...f, materialId: v }))}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4 }}>版本号</div>
              <Input
                style={{ width: 160 }}
                placeholder="如 V1.0"
                value={form.version}
                onChange={(e) => setForm((f) => ({ ...f, version: e.target.value }))}
              />
            </div>
            <div style={{ width: 260 }}>
              <div style={{ marginBottom: 4 }}>备注</div>
              <Input
                placeholder="可选"
                value={form.remark}
                onChange={(e) => setForm((f) => ({ ...f, remark: e.target.value }))}
              />
            </div>
          </Space>
          <div style={{ fontWeight: 600 }}>
            工序步骤（按顺序执行；工作中心/单件工时留空取工序默认值，序号自动编号）
          </div>
          <StepEditor
            steps={steps}
            onChange={setSteps}
            operationOptions={operationOptions}
            workCenterOptions={workCenterOptions}
          />
        </Space>
      </Modal>

      {/* 工序序列（树形查看）弹窗 */}
      <Modal
        title={`工序序列 - ${treeData?.materialCode ?? ''} ${treeData?.version ?? ''}`}
        open={treeOpen}
        onCancel={() => setTreeOpen(false)}
        footer={null}
        width={760}
      >
        <Table<(RoutingTree['steps'][number] & { key: string })>
          rowKey={(r) => r.key}
          size="small"
          loading={treeLoading}
          pagination={false}
          dataSource={(treeData?.steps ?? []).map((s) => ({ ...s, key: `seq-${s.seqNo}` }))}
          columns={[
            { title: '序号', dataIndex: 'seqNo', width: 64 },
            { title: '工序编码', dataIndex: 'operationCode', width: 140 },
            { title: '工序名称', dataIndex: 'operationName', ellipsis: true },
            { title: '工作中心', dataIndex: 'workCenterName', width: 130, render: (v) => v ?? '未指定' },
            {
              title: '生效单件工时(分钟)',
              dataIndex: 'effectiveUnitMinutes',
              width: 140,
              align: 'right',
              render: (v) => v ?? '-',
            },
            {
              title: '报工点',
              dataIndex: 'reportFlag',
              width: 80,
              render: (v: number) => (v === 1 ? '是' : '否'),
            },
          ]}
        />
      </Modal>

      {/* 复制新版本弹窗 */}
      <Modal
        title="复制为新版本"
        open={copyOpen}
        onCancel={() => setCopyOpen(false)}
        onOk={() => {
          if (!newVersion.trim()) return message.warning('请填写新版本号')
          copySubmit.run()
        }}
        confirmLoading={copySubmit.loading}
        destroyOnClose
      >
        <Input
          placeholder="新版本号，如 V2.0（复制后为草稿，可编辑步骤）"
          value={newVersion}
          onChange={(e) => setNewVersion(e.target.value)}
        />
      </Modal>
    </>
  )
}

/** 工作中心 Tab：每日产能工时维护（CRP 可用工时基数） */
function WorkCenterTab() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebounce(keyword)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<WorkCenterRow | null>(null)
  const [form] = Form.useForm<WorkCenterForm>()

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['mrp', 'work-centers'] })

  const saveSubmit = useSubmitting(
    async (values: WorkCenterForm) => {
      if (editing) await workCenterApi.update(editing.id, values)
      else await workCenterApi.create(values)
    },
    {
      successText: editing ? '工作中心已保存' : '工作中心已创建',
      onSuccess: () => {
        setOpen(false)
        invalidate()
      },
    },
  )

  const deleteSubmit = useSubmitting((id: number) => workCenterApi.remove(id), {
    successText: '工作中心已删除',
    onSuccess: invalidate,
  })

  const columns: ColumnsType<WorkCenterRow> = [
    { title: 'ID', dataIndex: 'id', width: 64 },
    { title: '编码', dataIndex: 'code', width: 150 },
    { title: '名称', dataIndex: 'name', width: 150 },
    { title: '所属车间', dataIndex: 'workshop', width: 120, render: (v) => v ?? '-' },
    {
      title: '每日产能工时(h)',
      dataIndex: 'capacityHoursPerDay',
      width: 130,
      align: 'right',
      render: (v) => <Text strong>{v}</Text>,
    },
    { title: '设备数', dataIndex: 'equipmentCount', width: 80, align: 'right' },
    { title: '状态', dataIndex: 'status', width: 80, render: renderEnableStatus },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v) => v ?? '-' },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      render: (_, row) => (
        <Space size={4}>
          <Button
            size="small"
            onClick={() => {
              setEditing(row)
              form.resetFields()
              form.setFieldsValue({
                name: row.name,
                workshop: row.workshop ?? undefined,
                capacityHoursPerDay: row.capacityHoursPerDay,
                equipmentCount: row.equipmentCount,
                status: row.status,
                remark: row.remark ?? undefined,
              })
              setOpen(true)
            }}
          >
            编辑
          </Button>
          <Popconfirm
            title="被工序/路线/工单任务引用时将无法删除"
            onConfirm={() => deleteSubmit.run(row.id)}
          >
            <Button size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <PageTable<WorkCenterRow>
        url="/mrp/work-centers"
        queryKey={['mrp', 'work-centers', debouncedKeyword]}
        params={{ keyword: debouncedKeyword || undefined }}
        columns={columns}
        toolbar={
          <>
            <Input.Search
              placeholder="编码 / 名称 / 车间搜索"
              style={{ width: 220 }}
              allowClear
              onSearch={(v) => setKeyword(v)}
            />
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditing(null)
                form.resetFields()
                form.setFieldsValue({ capacityHoursPerDay: 8, equipmentCount: 1, status: 'ENABLED' })
                setOpen(true)
              }}
            >
              新建工作中心
            </Button>
          </>
        }
      />

      <Modal
        title={editing ? `编辑工作中心 ${editing.code}` : '新建工作中心'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveSubmit.loading}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => saveSubmit.run(values)}
          initialValues={{ capacityHoursPerDay: 8, equipmentCount: 1, status: 'ENABLED' }}
        >
          <Form.Item
            name="name"
            label="工作中心名称"
            rules={[{ required: true, message: '请输入工作中心名称' }]}
          >
            <Input placeholder="如 冲压中心" maxLength={100} />
          </Form.Item>
          <Form.Item name="workshop" label="所属车间">
            <Input placeholder="可选" maxLength={100} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item
              name="capacityHoursPerDay"
              label="每日产能工时（小时/天）"
              rules={[{ required: true, message: '请输入每日产能工时' }]}
            >
              <InputNumber min={0.01} step={1} precision={2} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item
              name="equipmentCount"
              label="设备数"
              rules={[{ required: true, message: '请输入设备数' }]}
            >
              <InputNumber min={0} precision={0} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select
                style={{ width: 120 }}
                options={[
                  { value: 'ENABLED', label: '启用' },
                  { value: 'DISABLED', label: '停用' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={200} placeholder="可选" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

/** 工序定义 Tab：标准工序库（默认工作中心 / 单件工时） */
function OperationTab() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebounce(keyword)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<OperationRow | null>(null)
  const [form] = Form.useForm<OperationForm>()

  const { data: workCenters } = useQuery({
    queryKey: ['mrp', 'work-centers', 'all'],
    queryFn: workCenterApi.listAll,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['mrp', 'operations'] })

  const saveSubmit = useSubmitting(
    async (values: OperationForm) => {
      if (editing) await operationApi.update(editing.id, values)
      else await operationApi.create(values)
    },
    {
      successText: editing ? '工序已保存' : '工序已创建',
      onSuccess: () => {
        setOpen(false)
        invalidate()
      },
    },
  )

  const deleteSubmit = useSubmitting((id: number) => operationApi.remove(id), {
    successText: '工序已删除',
    onSuccess: invalidate,
  })

  const columns: ColumnsType<OperationRow> = [
    { title: 'ID', dataIndex: 'id', width: 64 },
    { title: '编码', dataIndex: 'code', width: 150 },
    { title: '工序名称', dataIndex: 'name', width: 140 },
    {
      title: '默认工作中心',
      key: 'defaultWorkCenter',
      width: 140,
      render: (_, row) => row.defaultWorkCenterName ?? '未指定',
    },
    { title: '准备时间(分钟/批)', dataIndex: 'setupMinutes', width: 130, align: 'right' },
    {
      title: '单件工时(分钟/件)',
      dataIndex: 'unitMinutes',
      width: 130,
      align: 'right',
      render: (v) => <Text strong>{v}</Text>,
    },
    {
      title: '外协',
      dataIndex: 'outsourceFlag',
      width: 70,
      render: (v: number) => (v === 1 ? '外协' : '自制'),
    },
    { title: '状态', dataIndex: 'status', width: 80, render: renderEnableStatus },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v) => v ?? '-' },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      render: (_, row) => (
        <Space size={4}>
          <Button
            size="small"
            onClick={() => {
              setEditing(row)
              form.resetFields()
              form.setFieldsValue({
                name: row.name,
                defaultWorkCenterId: row.defaultWorkCenterId ?? undefined,
                setupMinutes: row.setupMinutes,
                unitMinutes: row.unitMinutes,
                outsourceFlag: row.outsourceFlag,
                status: row.status,
                remark: row.remark ?? undefined,
              })
              setOpen(true)
            }}
          >
            编辑
          </Button>
          <Popconfirm
            title="被工艺路线步骤引用时将无法删除"
            onConfirm={() => deleteSubmit.run(row.id)}
          >
            <Button size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <PageTable<OperationRow>
        url="/mrp/operations"
        queryKey={['mrp', 'operations', debouncedKeyword]}
        params={{ keyword: debouncedKeyword || undefined }}
        columns={columns}
        toolbar={
          <>
            <Input.Search
              placeholder="编码 / 名称搜索"
              style={{ width: 220 }}
              allowClear
              onSearch={(v) => setKeyword(v)}
            />
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditing(null)
                form.resetFields()
                form.setFieldsValue({ setupMinutes: 0, unitMinutes: 1, outsourceFlag: 0, status: 'ENABLED' })
                setOpen(true)
              }}
            >
              新建工序
            </Button>
          </>
        }
      />

      <Modal
        title={editing ? `编辑工序 ${editing.code}` : '新建工序'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveSubmit.loading}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => saveSubmit.run(values)}
          initialValues={{ setupMinutes: 0, unitMinutes: 1, outsourceFlag: 0, status: 'ENABLED' }}
        >
          <Form.Item
            name="name"
            label="工序名称"
            rules={[{ required: true, message: '请输入工序名称' }]}
          >
            <Input placeholder="如 切割" maxLength={100} />
          </Form.Item>
          <Form.Item name="defaultWorkCenterId" label="默认工作中心">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="可选"
              options={(workCenters ?? []).map((w) => ({
                value: w.id,
                label: `${w.code} | ${w.name}`,
              }))}
            />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="setupMinutes" label="准备时间（分钟/批）">
              <InputNumber min={0} precision={0} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item
              name="unitMinutes"
              label="单件工时（分钟/件）"
              rules={[{ required: true, message: '请输入单件工时' }]}
            >
              <InputNumber min={0} step={0.5} precision={2} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="outsourceFlag" label="加工方式">
              <Select
                style={{ width: 110 }}
                options={[
                  { value: 0, label: '自制' },
                  { value: 1, label: '外协' },
                ]}
              />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select
                style={{ width: 110 }}
                options={[
                  { value: 'ENABLED', label: '启用' },
                  { value: 'DISABLED', label: '停用' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={200} placeholder="可选" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

/**
 * 工艺路线管理页（二期）：工艺路线（列表 + 步骤子表编辑 + 复制新版本 + 审核/停用 + 工序序列）
 * + 工作中心（每日产能工时）+ 工序定义（默认工作中心/单件工时）三个基础域。
 */
export default function RoutingPage() {
  return (
    <div className="erp-page">
      <PageHeader
        title="工艺路线"
        subtitle="成品工序序列定义；已审核路线在生产订单下达时自动生成工单工序任务（按工时顺排）"
      />
      <Tabs
        defaultActiveKey="routings"
        items={[
          { key: 'routings', label: '工艺路线', children: <RoutingTab /> },
          { key: 'workCenters', label: '工作中心', children: <WorkCenterTab /> },
          { key: 'operations', label: '工序定义', children: <OperationTab /> },
        ]}
      />
    </div>
  )
}
