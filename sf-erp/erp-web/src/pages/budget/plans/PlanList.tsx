import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Tag,
} from 'antd'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import StatusTag from '@/components/StatusTag'
import { useDebounce } from '@/hooks/useDebounce'
import { useSubmitting } from '@/hooks/useSubmitting'
import {
  planApi,
  PLAN_STATUS_OPTIONS,
  PLAN_STATUS_TEXT,
  type BudgetPlanRow,
  type BudgetPlanSavePayload,
  type BudgetControlWay,
} from '@/api/budget'
import { fmtMoney, PLAN_STATUS_SEMANTIC } from '../shared'
import MatrixModal from './MatrixModal'

/**
 * 预算方案列表页 —— 方案生命周期管理（新增/编制/复制/下达/关闭/删除）。
 * <p>
 * 状态机：草稿(可编制/修改/删除) → 已下达(锁定禁改) → 已关闭；
 * 编制走矩阵弹窗（部门×科目×12 月可编辑表格，见 MatrixModal）。
 */
export default function PlanList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>(undefined)
  const debouncedKeyword = useDebounce(keyword)

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<BudgetPlanRow | null>(null)
  const [matrixPlan, setMatrixPlan] = useState<BudgetPlanRow | null>(null)
  const [configPlan, setConfigPlan] = useState<BudgetPlanRow | null>(null)

  const [form] = Form.useForm<BudgetPlanSavePayload>()
  const [configForm] = Form.useForm<{ controlWay: BudgetControlWay; overPercent: number }>()

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['budget', 'plans'] })

  /* ---------- 新增/编辑 ---------- */
  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ budgetYear: new Date().getFullYear() })
    setFormOpen(true)
  }

  const openEdit = (row: BudgetPlanRow) => {
    setEditing(row)
    form.resetFields()
    form.setFieldsValue({
      planCode: row.planCode,
      planName: row.planName,
      budgetYear: row.budgetYear,
      remark: row.remark ?? undefined,
    })
    setFormOpen(true)
  }

  const savePlan = useSubmitting(
    async (values: BudgetPlanSavePayload) => {
      if (editing) await planApi.update(editing.id, values)
      else await planApi.create(values)
    },
    { successText: editing ? '修改成功' : '新增成功', onSuccess: () => { setFormOpen(false); invalidate() } },
  )

  /* ---------- 状态流转（危险操作一律 Popconfirm） ---------- */
  const release = useSubmitting(
    async (id: number) => await planApi.release(id),
    { successText: '方案已下达，明细已锁定', onSuccess: invalidate },
  )
  const close = useSubmitting(
    async (id: number) => await planApi.close(id),
    { successText: '方案已关闭', onSuccess: invalidate },
  )
  const remove = useSubmitting(
    async (id: number) => await planApi.remove(id),
    { successText: '删除成功', onSuccess: invalidate },
  )
  const copy = useSubmitting(
    async (row: BudgetPlanRow) => {
      const newId = await planApi.copy(row.id)
      message.info(`已复制为 ${row.budgetYear + 1} 年度草稿方案 #${newId}`)
    },
    { successText: null, onSuccess: invalidate },
  )

  /* ---------- 控制配置 ---------- */
  const openConfig = (row: BudgetPlanRow) => {
    setConfigPlan(row)
    configForm.resetFields()
    configForm.setFieldsValue({
      controlWay: row.controlWay ?? 'WARN',
      overPercent: row.overPercent ?? 100,
    })
  }

  const saveConfig = useSubmitting(
    async (values: { controlWay: BudgetControlWay; overPercent: number }) => {
      if (configPlan) await planApi.saveConfig(configPlan.id, values)
    },
    {
      successText: '控制配置已保存',
      onSuccess: () => {
        setConfigPlan(null)
        invalidate()
      },
    },
  )

  const columns = [
    { title: '方案编码', dataIndex: 'planCode', width: 130 },
    { title: '方案名称', dataIndex: 'planName', width: 180, ellipsis: true },
    { title: '年度', dataIndex: 'budgetYear', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_: unknown, row: BudgetPlanRow) => (
        <StatusTag value={PLAN_STATUS_TEXT[row.status]} semantic={PLAN_STATUS_SEMANTIC[row.status]} />
      ),
    },
    {
      title: '预算总额',
      dataIndex: 'totalAmount',
      width: 130,
      align: 'right' as const,
      render: (v: number) => fmtMoney(v),
    },
    {
      title: '控制方式',
      dataIndex: 'controlWay',
      width: 130,
      render: (v: BudgetControlWay | null, row: BudgetPlanRow) =>
        v === 'BLOCK' ? (
          <Tag color="error">阻止 ≥{row.overPercent ?? 100}%</Tag>
        ) : v === 'WARN' ? (
          <Tag color="warning">警告放行 ≥{row.overPercent ?? 100}%</Tag>
        ) : (
          <Tag>默认警告 ≥100%</Tag>
        ),
    },
    { title: '编制人', dataIndex: 'prepareByName', width: 100 },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      key: 'actions',
      width: 330,
      fixed: 'right' as const,
      render: (_: unknown, row: BudgetPlanRow) => (
        <Space size={0} wrap>
          <Button type="link" size="small" onClick={() => setMatrixPlan(row)}>
            {row.status === 'DRAFT' ? '编制' : '查看'}
          </Button>
          {row.status === 'DRAFT' && (
            <>
              <HasPerm code="budget:plan:edit">
                <Button type="link" size="small" onClick={() => openEdit(row)}>
                  编辑
                </Button>
              </HasPerm>
              <HasPerm code="budget:plan:audit">
                <Popconfirm
                  title="下达该预算方案？"
                  description="下达后明细锁定不可修改，并开始参与预算控制"
                  onConfirm={() => release.run(row.id)}
                >
                  <Button type="link" size="small">
                    下达
                  </Button>
                </Popconfirm>
              </HasPerm>
              <HasPerm code="budget:plan:delete">
              <Popconfirm title="确认删除该方案？" description="删除后不可恢复" onConfirm={() => remove.run(row.id)}>
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>
              </HasPerm>
            </>
          )}
          {row.status === 'RELEASED' && (
            <HasPerm code="budget:plan:audit">
              <Popconfirm
                title="关闭该预算方案？"
                description="关闭后不再参与预算控制，仅留存档"
                onConfirm={() => close.run(row.id)}
              >
                <Button type="link" size="small" danger>
                  关闭
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          <Button type="link" size="small" onClick={() => openConfig(row)} disabled={row.status === 'CLOSED'}>
            控制配置
          </Button>
          <HasPerm code="budget:plan:add">
            <Popconfirm
              title={`复制为 ${row.budgetYear + 1} 年度草稿方案？`}
              description="明细与控制配置一并复制，可再调整"
              onConfirm={() => copy.run(row)}
            >
              <Button type="link" size="small">
                复制
              </Button>
            </Popconfirm>
          </HasPerm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="预算编制"
        subtitle="按部门×科目编制年度 12 个月预算，下达后参与预算控制与执行分析"
        extra={<HasPerm code="budget:plan:add"><Button type="primary" onClick={openCreate}>新增方案</Button></HasPerm>}
      />

      <PageTable<BudgetPlanRow>
        url="/budget/plans"
        queryKey={['budget', 'plans', debouncedKeyword, status]}
        params={{ keyword: debouncedKeyword || undefined, status }}
        columns={columns}
        toolbar={
          <>
            <Input.Search
              placeholder="方案编码/名称"
              allowClear
              style={{ width: 220 }}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <Select
              placeholder="状态"
              allowClear
              style={{ width: 120 }}
              value={status}
              options={PLAN_STATUS_OPTIONS}
              onChange={(v) => setStatus(v)}
            />
          </>
        }
      />

      {/* 新增/编辑方案 */}
      <Modal
        title={editing ? '编辑预算方案' : '新增预算方案'}
        open={formOpen}
        onCancel={() => setFormOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={savePlan.loading}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => savePlan.run(values)}
          disabled={savePlan.loading}
        >
          <Form.Item
            name="planCode"
            label="方案编码"
            rules={[
              { required: true, message: '请输入方案编码' },
              { max: 40, message: '方案编码不能超过 40 字符' },
            ]}
          >
            <Input placeholder="如 YS2026-001" />
          </Form.Item>
          <Form.Item
            name="planName"
            label="方案名称"
            rules={[
              { required: true, message: '请输入方案名称' },
              { max: 100, message: '方案名称不能超过 100 字符' },
            ]}
          >
            <Input placeholder="如 2026 年度全面预算" />
          </Form.Item>
          <Form.Item
            name="budgetYear"
            label="预算年度"
            rules={[{ required: true, message: '请输入预算年度' }]}
          >
            <InputNumber style={{ width: '100%' }} min={2000} max={2100} precision={0} placeholder="2026" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={500} placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 矩阵编制弹窗 */}
      <MatrixModal
        plan={matrixPlan}
        open={!!matrixPlan}
        onClose={() => setMatrixPlan(null)}
        onSuccess={invalidate}
      />

      {/* 控制配置弹窗 */}
      <Modal
        title={`控制配置 — ${configPlan?.planName ?? ''}`}
        open={!!configPlan}
        onCancel={() => setConfigPlan(null)}
        onOk={() => configForm.submit()}
        confirmLoading={saveConfig.loading}
        destroyOnClose
      >
        <Form form={configForm} layout="vertical" onFinish={(values) => saveConfig.run(values)}>
          <Form.Item name="controlWay" label="控制方式" rules={[{ required: true, message: '请选择控制方式' }]}>
            <Radio.Group>
              <Radio value="WARN">警告放行（超预算仅提示）</Radio>
              <Radio value="BLOCK">阻止（超预算拦截业务）</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item
            name="overPercent"
            label="超预算阈值（%）"
            rules={[{ required: true, message: '请输入超预算阈值' }]}
            extra="预算占用率 =（已用 + 本次）/ 当月预算 × 100%，达到阈值触发控制"
          >
            <InputNumber style={{ width: '100%' }} min={1} max={1000} precision={2} placeholder="100" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
