import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
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
import dayjs, { type Dayjs } from 'dayjs'
import {
  pageAssets,
  createAsset,
  updateAsset,
  deleteAsset,
  disposeAsset,
  listCategories,
  type AssetVO,
  type AssetForm,
  type AssetCategoryVO,
  type DisposeMethod,
} from '@/api/asset'
import { getDeptTree, type SysDept } from '@/api/system'

const { Title } = Typography

/** 部门树节点（TreeSelect 数据） */
interface DeptTreeNode {
  title: string
  value: number
  children?: DeptTreeNode[]
}

/** 资产状态 → 展示标签 */
const STATUS_OPTIONS: { label: string; value: AssetVO['status'] }[] = [
  { label: '在用', value: 'IN_USE' },
  { label: '闲置', value: 'IDLE' },
  { label: '处置', value: 'DISPOSED' },
]

/** 处置方式选项 */
const DISPOSE_METHODS: { label: string; value: DisposeMethod }[] = [
  { label: '出售', value: 'SELL' },
  { label: '报废', value: 'SCRAP' },
  { label: '捐赠', value: 'DONATE' },
]

/** 部门树 → AntD TreeSelect 数据 */
function deptToTreeData(nodes: SysDept[]): DeptTreeNode[] {
  return nodes.map((n) => ({
    title: n.name,
    value: n.id,
    children: n.children.length ? deptToTreeData(n.children) : undefined,
  }))
}

/** 状态 → Tag 颜色 */
function statusTag(status: AssetVO['status']) {
  if (status === 'IN_USE') return <Tag color="green">在用</Tag>
  if (status === 'IDLE') return <Tag color="orange">闲置</Tag>
  return <Tag color="default">处置</Tag>
}

/** 表单值类型（DatePicker/InputNumber 转 string/number 后提交） */
type CardFormValues = {
  name: string
  categoryId: number
  spec?: string
  location?: string
  deptId?: number
  keeper?: string
  originalValue: number
  residualValue?: number
  expectedLifeMonths?: number
  accountDate?: Dayjs
  status?: AssetVO['status']
  remark?: string
}

/** 资产卡片表单 → 提交对象 */
function toSubmit(values: CardFormValues): AssetForm {
  return {
    ...values,
    accountDate: values.accountDate ? values.accountDate.format('YYYY-MM-DD') : undefined,
  }
}

/**
 * 资产卡片页：分页查询（类别/状态/部门筛选）、新增（类别下拉带折旧政策回显）、
 * 编辑、处置（净值转固定资产清理）、删除。
 * 服务端态走 TanStack Query；写操作成功后失效列表缓存自动刷新。
 */
export default function AssetCardList() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [categoryId, setCategoryId] = useState<number | undefined>()
  const [status, setStatus] = useState<AssetVO['status'] | undefined>()
  const [deptId, setDeptId] = useState<number | undefined>()
  const [keyword, setKeyword] = useState('')

  const [editing, setEditing] = useState<AssetVO | null>(null)
  const [open, setOpen] = useState(false)
  const [disposeTarget, setDisposeTarget] = useState<AssetVO | null>(null)
  const [form] = Form.useForm<CardFormValues>()
  const [disposeForm] = Form.useForm<{ method: DisposeMethod; income: number; remark?: string }>()

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['asset', 'cards', keyword, categoryId, status, deptId, pageNum, pageSize],
    queryFn: () =>
      pageAssets({
        pageNum,
        pageSize,
        keyword: keyword || undefined,
        categoryId,
        status,
        deptId,
      }),
  })
  const { data: categories } = useQuery({
    queryKey: ['asset', 'categories'],
    queryFn: listCategories,
  })
  const { data: deptTree } = useQuery({ queryKey: ['system', 'depts', 'tree'], queryFn: () => getDeptTree() })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['asset', 'cards'] })

  const saveMutation = useMutation({
    mutationFn: async (values: CardFormValues) => {
      const submit = toSubmit(values)
      if (editing) {
        await updateAsset(editing.id, { ...submit, status: values.status ?? editing.status })
      } else {
        await createAsset(submit)
      }
    },
    onSuccess: () => {
      message.success(editing ? '修改成功（财务字段计提后锁定，未变更部分不受影响）' : '新增成功，已自动生成入账凭证')
      setOpen(false)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteAsset,
    onSuccess: () => {
      message.success('删除成功')
      invalidate()
    },
  })

  const disposeMutation = useMutation({
    mutationFn: ({ id, values }: { id: number; values: { method: DisposeMethod; income: number; remark?: string } }) =>
      disposeAsset(id, values),
    onSuccess: (result) => {
      message.success(`处置完成：净值 ${result.netValue} 转固定资产清理，凭证 ${result.voucherNo}`)
      setDisposeTarget(null)
      invalidate()
    },
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ accountDate: dayjs() })
    setOpen(true)
  }

  const openEdit = (asset: AssetVO) => {
    setEditing(asset)
    form.resetFields()
    form.setFieldsValue({
      name: asset.name,
      categoryId: asset.categoryId,
      spec: asset.spec ?? undefined,
      location: asset.location ?? undefined,
      deptId: asset.deptId ?? undefined,
      keeper: asset.keeper ?? undefined,
      originalValue: asset.originalValue,
      residualValue: asset.residualValue,
      expectedLifeMonths: asset.expectedLifeMonths,
      accountDate: dayjs(asset.accountDate),
      status: asset.status === 'DISPOSED' ? undefined : asset.status,
      remark: asset.remark ?? undefined,
    })
    setOpen(true)
  }

  /** 当前选中类别（用于折旧政策回显提示） */
  const selectedCategoryId = Form.useWatch('categoryId', form)
  const selectedCategory: AssetCategoryVO | undefined = (categories ?? []).find(
    (c) => c.id === selectedCategoryId,
  )
  const financialLocked = !!editing && (editing.accumulatedDepreciation > 0 || editing.depreciatedMonths > 0)

  const columns: ColumnsType<AssetVO> = [
    { title: '编码', dataIndex: 'assetCode', width: 90 },
    { title: '名称', dataIndex: 'name', width: 160, ellipsis: true },
    { title: '类别', dataIndex: 'categoryName', width: 100, render: (v) => v ?? '-' },
    { title: '部门', dataIndex: 'deptName', width: 90, render: (v) => v ?? '-' },
    {
      title: '原值',
      dataIndex: 'originalValue',
      width: 110,
      align: 'right',
      render: (v: number) => v.toLocaleString('zh-CN', { minimumFractionDigits: 2 }),
    },
    {
      title: '累计折旧',
      dataIndex: 'accumulatedDepreciation',
      width: 110,
      align: 'right',
      render: (v: number) => v.toLocaleString('zh-CN', { minimumFractionDigits: 2 }),
    },
    {
      title: '净值',
      dataIndex: 'netValue',
      width: 110,
      align: 'right',
      render: (v: number) => v.toLocaleString('zh-CN', { minimumFractionDigits: 2 }),
    },
    {
      title: '已提/年限(月)',
      key: 'months',
      width: 110,
      align: 'center',
      render: (_, r) => `${r.depreciatedMonths}/${r.expectedLifeMonths}`,
    },
    { title: '状态', dataIndex: 'status', width: 80, render: (s) => statusTag(s) },
    { title: '入账日期', dataIndex: 'accountDate', width: 105 },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, record) => (
        <Space size={4}>
          <HasPerm code="asset:asset:edit">
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          </HasPerm>
          <HasPerm code="asset:asset:audit">
            <Button
              type="link"
              size="small"
              danger={record.status !== 'DISPOSED'}
              disabled={record.status === 'DISPOSED'}
              onClick={() => {
                setDisposeTarget(record)
                disposeForm.setFieldsValue({ method: 'SELL', income: 0 })
              }}
            >
              处置
            </Button>
          </HasPerm>
          <HasPerm code="asset:asset:delete">
          <Popconfirm title="确认删除该资产？（已计提/已处置会被拒绝）" onConfirm={() => deleteMutation.mutate(record.id)}>
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
        资产卡片
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Input.Search
            placeholder="资产编码 / 名称"
            style={{ width: 220 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setKeyword(v)
            }}
          />
          <Select
            placeholder="类别"
            style={{ width: 140 }}
            allowClear
            options={(categories ?? []).map((c) => ({ label: `${c.code} ${c.name}`, value: c.id }))}
            value={categoryId}
            onChange={(v) => {
              setPageNum(1)
              setCategoryId(v)
            }}
          />
          <Select
            placeholder="状态"
            style={{ width: 110 }}
            allowClear
            options={STATUS_OPTIONS}
            value={status}
            onChange={(v) => {
              setPageNum(1)
              setStatus(v)
            }}
          />
          <TreeSelect
            placeholder="部门"
            style={{ width: 140 }}
            allowClear
            treeDefaultExpandAll
            treeData={deptToTreeData(deptTree ?? [])}
            value={deptId}
            onChange={(v) => {
              setPageNum(1)
              setDeptId(v)
            }}
          />
          <HasPerm code="asset:asset:add">
          <Button type="primary" onClick={openCreate}>
            新增资产
          </Button>
          </HasPerm>
        </Space>

        <Table<AssetVO>
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
        title={editing ? `修改资产 - ${editing.assetCode}` : '新增资产'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
        width={640}
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="name"
              label="资产名称"
              rules={[{ required: true, message: '请输入资产名称' }]}
              style={{ width: 280 }}
            >
              <Input placeholder="如：笔记本 ThinkPad X1" maxLength={100} />
            </Form.Item>
            <Form.Item
              name="categoryId"
              label="资产类别"
              rules={[{ required: true, message: '请选择类别' }]}
              style={{ width: 280 }}
              extra={
                selectedCategory
                  ? `折旧政策：${selectedCategory.depreciationMethod === 'AVG' ? '平均年限法' : selectedCategory.depreciationMethod} / ${selectedCategory.expectedLifeMonths} 月 / 残值率 ${selectedCategory.residualRate}%`
                  : '残值与年限留空时按类别折旧政策自动带出'
              }
            >
              <Select
                placeholder="选择类别"
                options={(categories ?? []).map((c) => ({ label: `${c.code} ${c.name}`, value: c.id }))}
                disabled={financialLocked}
              />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="spec" label="规格型号" style={{ width: 280 }}>
              <Input placeholder="规格型号" maxLength={200} />
            </Form.Item>
            <Form.Item name="location" label="存放地点" style={{ width: 280 }}>
              <Input placeholder="存放地点" maxLength={100} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="deptId" label="使用部门" style={{ width: 280 }}>
              <TreeSelect
                placeholder="折旧费用按部门汇总"
                allowClear
                treeDefaultExpandAll
                treeData={deptToTreeData(deptTree ?? [])}
              />
            </Form.Item>
            <Form.Item name="keeper" label="责任人" style={{ width: 280 }}>
              <Input placeholder="责任人" maxLength={50} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="originalValue"
              label="原值"
              rules={[{ required: true, message: '请输入原值' }]}
              style={{ width: 136 }}
              extra={editing ? '新增时自动生成入账凭证：借 固定资产 / 贷 应付账款' : undefined}
            >
              <InputNumber min={0.01} precision={2} style={{ width: '100%' }} placeholder="12000" disabled={financialLocked} />
            </Form.Item>
            <Form.Item
              name="residualValue"
              label="残值"
              style={{ width: 136 }}
              extra={selectedCategory && !editing ? `留空按 ${selectedCategory.residualRate}% 计算` : undefined}
            >
              <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder="自动" disabled={financialLocked} />
            </Form.Item>
            <Form.Item
              name="expectedLifeMonths"
              label="年限(月)"
              style={{ width: 136 }}
              extra={selectedCategory && !editing ? `留空取 ${selectedCategory.expectedLifeMonths}` : undefined}
            >
              <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="自动" disabled={financialLocked} />
            </Form.Item>
            <Form.Item
              name="accountDate"
              label="入账日期"
              style={{ width: 160 }}
              rules={[{ required: true, message: '请选择入账日期' }]}
            >
              <DatePicker style={{ width: '100%' }} disabled={financialLocked} />
            </Form.Item>
          </Space>
          {editing && editing.status !== 'DISPOSED' && (
            <Form.Item name="status" label="状态（处置需走处置流程）">
              <Select
                style={{ width: 160 }}
                options={STATUS_OPTIONS.filter((o) => o.value !== 'DISPOSED')}
              />
            </Form.Item>
          )}
          {financialLocked && (
            <Typography.Text type="warning" style={{ fontSize: 12 }}>
              该资产已计提折旧：类别 / 原值 / 残值 / 年限 / 入账日期已锁定，只能修改基本信息与状态。
            </Typography.Text>
          )}
          <Form.Item name="remark" label="备注" style={{ marginBottom: 0, marginTop: 12 }}>
            <Input.TextArea placeholder="备注" maxLength={200} rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 处置弹窗 */}
      <Modal
        title={`资产处置 - ${disposeTarget?.assetCode ?? ''} ${disposeTarget?.name ?? ''}`}
        open={!!disposeTarget}
        onCancel={() => setDisposeTarget(null)}
        onOk={() =>
          disposeForm.validateFields().then((values) =>
            disposeTarget && disposeMutation.mutate({ id: disposeTarget.id, values }),
          )
        }
        confirmLoading={disposeMutation.isPending}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
          当前净值 {disposeTarget?.netValue?.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}、
          累计折旧 {disposeTarget?.accumulatedDepreciation?.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}。
          处置后生成平衡凭证：借 累计折旧 + 借 固定资产清理（净值）/ 贷 固定资产（原值）；
          清理收入 &gt; 0 时追加 借 库存现金 / 贷 固定资产清理。资产状态置为"处置"，此后不再计提折旧。
        </Typography.Paragraph>
        <Form form={disposeForm} layout="vertical">
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item
              name="method"
              label="处置方式"
              rules={[{ required: true, message: '请选择处置方式' }]}
              style={{ width: 200 }}
            >
              <Select options={DISPOSE_METHODS} />
            </Form.Item>
            <Form.Item
              name="income"
              label="清理收入"
              rules={[{ required: true, message: '无收入填 0' }]}
              style={{ width: 200 }}
            >
              <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder="0.00" />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="凭证说明">
            <Input.TextArea placeholder="写入处置凭证备注" maxLength={200} rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
