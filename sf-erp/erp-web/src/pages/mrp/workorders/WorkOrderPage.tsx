import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Drawer,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  issueApi,
  mrpApi,
  outsourceApi,
  workOrderApi,
  type MaterialIssueRow,
  type OutsourceRow,
  type WorkOrderRow,
} from '@/api/mrp'
import MaterialSelect from '../components/MaterialSelect'

const { Title, Text } = Typography

const WO_STATUS: Record<string, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  RELEASED: { label: '已下达', color: 'processing' },
  PRODUCING: { label: '生产中', color: 'geekblue' },
  FINISHED: { label: '已完工', color: 'success' },
  CLOSED: { label: '已关闭', color: 'warning' },
}

const OS_STATUS: Record<string, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  ISSUED: { label: '已发料', color: 'processing' },
  RECEIVED: { label: '已入库', color: 'success' },
}

/**
 * 生产订单页：订单全生命周期（下达/领料/完工/关闭）+ 委外加工（简化流程）。
 */
export default function WorkOrderPage() {
  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        生产订单
      </Title>
      <Tabs
        items={[
          { key: 'orders', label: '生产订单', children: <WorkOrderTab /> },
          { key: 'outsource', label: '委外加工', children: <OutsourceTab /> },
        ]}
      />
    </div>
  )
}

// ==================== 生产订单 Tab ====================

function WorkOrderTab() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [pageNum, setPageNum] = useState(1)

  // 订单编辑
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [woForm, setWoForm] = useState<{
    materialId?: number
    qty: number
    warehouseId?: number
    pickType?: 'DIRECT' | 'BACKFLUSH'
    backflushWarehouseId?: number
    planStartDate?: string
    planEndDate?: string
    remark: string
  }>({ qty: 10, remark: '', pickType: 'DIRECT' })

  // 详情抽屉
  const [drawerId, setDrawerId] = useState<number | null>(null)

  // 领料弹窗（两步：仓库→生成草稿；明细→审核）
  const [issueOpen, setIssueOpen] = useState(false)
  const [issueWoId, setIssueWoId] = useState<number | null>(null)
  const [issueWarehouseId, setIssueWarehouseId] = useState<number | undefined>()
  const [draftIssueId, setDraftIssueId] = useState<number | null>(null)
  const [actualQtyMap, setActualQtyMap] = useState<Record<number, number>>({})

  // 完工弹窗
  const [finishOpen, setFinishOpen] = useState(false)
  const [finishWoId, setFinishWoId] = useState<number | null>(null)
  const [finishForm, setFinishForm] = useState<{ goodQty: number; badQty: number; warehouseId?: number }>(
    { goodQty: 0, badQty: 0 },
  )

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['mrp', 'work-orders', keyword, status, pageNum],
    queryFn: () => workOrderApi.page({ pageNum, pageSize: 10, keyword: keyword || undefined, status }),
  })

  const { data: warehouses } = useQuery({
    queryKey: ['mrp', 'warehouses'],
    queryFn: () => mrpApi.warehouses(),
  })

  const { data: drawerOrder } = useQuery({
    queryKey: ['mrp', 'work-order', drawerId],
    queryFn: () => workOrderApi.detail(drawerId as number),
    enabled: drawerId != null,
  })

  const { data: drawerIssues } = useQuery({
    queryKey: ['mrp', 'issues', drawerId],
    queryFn: () => issueApi.page({ pageNum: 1, pageSize: 50, workOrderId: drawerId as number }),
    enabled: drawerId != null,
  })

  const { data: draftIssue } = useQuery({
    queryKey: ['mrp', 'issue', draftIssueId],
    queryFn: () => issueApi.detail(draftIssueId as number),
    enabled: draftIssueId != null,
  })

  const invalidateOrders = () => queryClient.invalidateQueries({ queryKey: ['mrp', 'work-orders'] })
  const invalidateIssues = () => {
    queryClient.invalidateQueries({ queryKey: ['mrp', 'issues'] })
    queryClient.invalidateQueries({ queryKey: ['mrp', 'issue'] })
  }

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        materialId: woForm.materialId as number,
        qty: woForm.qty,
        warehouseId: woForm.warehouseId,
        pickType: woForm.pickType ?? 'DIRECT',
        backflushWarehouseId: woForm.backflushWarehouseId,
        planStartDate: woForm.planStartDate,
        planEndDate: woForm.planEndDate,
        remark: woForm.remark || undefined,
      }
      if (editingId) await workOrderApi.update(editingId, payload)
      else await workOrderApi.create(payload)
    },
    onSuccess: () => {
      message.success('生产订单已保存（草稿）')
      setEditorOpen(false)
      invalidateOrders()
    },
  })

  const actionMutation = useMutation({
    mutationFn: ({ action, id }: { action: 'release' | 'close' | 'delete'; id: number }) => {
      if (action === 'release') return workOrderApi.release(id)
      if (action === 'close') return workOrderApi.close(id)
      return workOrderApi.remove(id)
    },
    onSuccess: (_d, vars) => {
      const tips: Record<string, string> = {
        release: '订单已下达',
        close: '订单已关闭',
        delete: '订单已删除',
      }
      message.success(tips[vars.action])
      invalidateOrders()
    },
  })

  const finishMutation = useMutation({
    mutationFn: () =>
      workOrderApi.finish(finishWoId as number, {
        goodQty: finishForm.goodQty,
        badQty: finishForm.badQty,
        warehouseId: finishForm.warehouseId as number,
      }),
    onSuccess: () => {
      message.success('完工入库成功')
      setFinishOpen(false)
      invalidateOrders()
    },
  })

  // 领料：创建草稿（自动按BOM带出）
  const createIssueMutation = useMutation({
    mutationFn: () =>
      issueApi.create({
        workOrderId: issueWoId as number,
        warehouseId: issueWarehouseId as number,
      }),
    onSuccess: (id) => {
      message.success('领料单已生成（草稿），请核对实领数量后审核')
      setDraftIssueId(id)
      invalidateIssues()
    },
  })

  // 领料：审核扣库存
  const auditIssueMutation = useMutation({
    mutationFn: async () => {
      const issue = draftIssue
      if (!issue) return
      const items = issue.items
        .map((i) => ({ materialId: i.materialId, actualQty: actualQtyMap[i.materialId] ?? i.actualQty }))
        .filter((i) => i.actualQty > 0)
      await issueApi.update(issue.id, {
        workOrderId: issue.workOrderId,
        warehouseId: issue.warehouseId,
        items,
      })
      return issueApi.audit(issue.id)
    },
    onSuccess: () => {
      message.success('领料审核完成，库存已扣减')
      setIssueOpen(false)
      setDraftIssueId(null)
      setActualQtyMap({})
      invalidateIssues()
      invalidateOrders()
    },
  })

  const deleteIssueMutation = useMutation({
    mutationFn: (id: number) => issueApi.remove(id),
    onSuccess: () => {
      message.success('草稿领料单已删除')
      setIssueOpen(false)
      setDraftIssueId(null)
      invalidateIssues()
    },
  })

  const drawerIssueAudit = useMutation({
    mutationFn: (id: number) => issueApi.audit(id),
    onSuccess: () => {
      message.success('领料审核完成，库存已扣减')
      invalidateIssues()
      invalidateOrders()
    },
  })

  const columns: ColumnsType<WorkOrderRow> = [
    { title: '订单号', dataIndex: 'orderNo', width: 140 },
    { title: '成品编码', dataIndex: 'materialCode', width: 110 },
    { title: '成品名称', dataIndex: 'materialName', ellipsis: true },
    { title: '数量', dataIndex: 'qty', width: 80, align: 'right' },
    { title: 'BOM版本', dataIndex: 'bomVersion', width: 80 },
    {
      title: '领料方式',
      dataIndex: 'pickType',
      width: 90,
      render: (v: string | null) =>
        v === 'BACKFLUSH' ? <Tag color="processing">倒冲</Tag> : <Tag>直接领料</Tag>,
    },
    { title: '计划开始', dataIndex: 'planStartDate', width: 105 },
    { title: '计划结束', dataIndex: 'planEndDate', width: 105 },
    {
      title: '完工(良/不良)',
      key: 'finish',
      width: 110,
      align: 'right',
      render: (_, row) => `${row.finishQty} / ${row.badQty}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 85,
      render: (v: string) => {
        const s = WO_STATUS[v]
        return s ? <Tag color={s.color}>{s.label}</Tag> : v
      },
    },
    {
      title: '操作',
      key: 'op',
      width: 300,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button size="small" onClick={() => setDrawerId(row.id)}>
            详情
          </Button>
          {row.status === 'DRAFT' && (
            <>
              <Button
                size="small"
                onClick={() => {
                  setEditingId(row.id)
                  setWoForm({
                    materialId: row.materialId,
                    qty: row.qty,
                    warehouseId: row.warehouseId ?? undefined,
                    pickType: row.pickType ?? 'DIRECT',
                    backflushWarehouseId: row.backflushWarehouseId ?? undefined,
                    planStartDate: row.planStartDate ?? undefined,
                    planEndDate: row.planEndDate ?? undefined,
                    remark: row.remark ?? '',
                  })
                  setEditorOpen(true)
                }}
              >
                编辑
              </Button>
              <Popconfirm title="下达该订单？（校验BOM有效）" onConfirm={() => actionMutation.mutate({ action: 'release', id: row.id })}>
                <Button size="small" type="primary" ghost>
                  下达
                </Button>
              </Popconfirm>
              <Popconfirm title="删除草稿订单？" onConfirm={() => actionMutation.mutate({ action: 'delete', id: row.id })}>
                <Button size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            </>
          )}
          {(row.status === 'RELEASED' || row.status === 'PRODUCING') && (
            <>
              <Button
                size="small"
                type="primary"
                ghost
                onClick={() => {
                  setIssueWoId(row.id)
                  setIssueWarehouseId(row.warehouseId ?? undefined)
                  setDraftIssueId(null)
                  setActualQtyMap({})
                  setIssueOpen(true)
                }}
              >
                领料
              </Button>
              <Button
                size="small"
                onClick={() => {
                  setFinishWoId(row.id)
                  setFinishForm({
                    goodQty: Number(row.qty) - Number(row.finishQty) - Number(row.badQty),
                    badQty: 0,
                    warehouseId: row.warehouseId ?? undefined,
                  })
                  setFinishOpen(true)
                }}
              >
                完工入库
              </Button>
            </>
          )}
          {row.status === 'FINISHED' && (
            <Popconfirm title="关闭该订单？" onConfirm={() => actionMutation.mutate({ action: 'close', id: row.id })}>
              <Button size="small">关闭</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  const issueColumns: ColumnsType<MaterialIssueRow> = [
    { title: '领料单号', dataIndex: 'issueNo', width: 140 },
    { title: '仓库', dataIndex: 'warehouseId', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 85,
      render: (v: string) => (v === 'DRAFT' ? <Tag>草稿</Tag> : <Tag color="success">已审核</Tag>),
    },
    {
      title: '子件/实领',
      key: 'items',
      ellipsis: true,
      render: (_, row) =>
        row.items
          .map((i) => `${i.materialCode ?? i.materialId}×${i.actualQty}`)
          .join('，'),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'op',
      width: 130,
      render: (_, row) =>
        row.status === 'DRAFT' ? (
          <Space size={4}>
            <Popconfirm title="审核将扣减库存，确认？" onConfirm={() => drawerIssueAudit.mutate(row.id)}>
              <Button size="small" type="primary" ghost loading={drawerIssueAudit.isPending}>
                审核
              </Button>
            </Popconfirm>
            <Button size="small" danger onClick={() => deleteIssueMutation.mutate(row.id)}>
              删除
            </Button>
          </Space>
        ) : (
          <Text type="secondary">已扣库存</Text>
        ),
    },
  ]

  return (
    <>
      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Input.Search
            placeholder="成品物料编码 / 名称搜索"
            style={{ width: 230 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setKeyword(v)
            }}
          />
          <Select
            placeholder="状态筛选"
            allowClear
            style={{ width: 120 }}
            value={status}
            onChange={(v) => {
              setPageNum(1)
              setStatus(v)
            }}
            options={Object.entries(WO_STATUS).map(([value, s]) => ({ value, label: s.label }))}
          />
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingId(null)
              setWoForm({ qty: 10, remark: '' })
              setEditorOpen(true)
            }}
          >
            新建订单
          </Button>
        </Space>
        <Table<WorkOrderRow>
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isLoading || isFetching}
          pagination={{
            total: data?.total ?? 0,
            current: data?.pageNum ?? 1,
            pageSize: data?.pageSize ?? 10,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p) => setPageNum(p),
          }}
        />
      </Card>

      {/* 订单编辑弹窗 */}
      <Modal
        title={editingId ? `编辑订单 #${editingId}` : '新建生产订单'}
        open={editorOpen}
        onCancel={() => setEditorOpen(false)}
        onOk={() => {
          if (!woForm.materialId) return message.warning('请选择成品物料')
          saveMutation.mutate()
        }}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div>
            <div style={{ marginBottom: 4 }}>成品物料（须有已审核 BOM）</div>
            <MaterialSelect
              value={woForm.materialId}
              onChange={(v) => setWoForm((f) => ({ ...f, materialId: v }))}
            />
          </div>
          <Space wrap>
            <div>
              <div style={{ marginBottom: 4 }}>生产数量</div>
              <InputNumber
                min={0.0001}
                style={{ width: 140 }}
                value={woForm.qty}
                onChange={(v) => setWoForm((f) => ({ ...f, qty: v ?? 1 }))}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4 }}>默认入库仓库</div>
              <Select
                style={{ width: 180 }}
                allowClear
                placeholder="选择仓库"
                value={woForm.warehouseId}
                onChange={(v) => setWoForm((f) => ({ ...f, warehouseId: v }))}
                options={(warehouses ?? []).map((w) => ({ value: w.id, label: w.name }))}
              />
            </div>
          </Space>
          <Space wrap>
            <div>
              <div style={{ marginBottom: 4 }}>领料方式</div>
              <Select
                style={{ width: 200 }}
                value={woForm.pickType ?? 'DIRECT'}
                onChange={(v) => setWoForm((f) => ({ ...f, pickType: v }))}
                options={[
                  { value: 'DIRECT', label: '直接领料（手工领料单）' },
                  { value: 'BACKFLUSH', label: '倒冲（完工自动扣料）' },
                ]}
              />
            </div>
            {woForm.pickType === 'BACKFLUSH' && (
              <div>
                <div style={{ marginBottom: 4 }}>倒冲扣料仓库（空=入库仓库）</div>
                <Select
                  style={{ width: 180 }}
                  allowClear
                  placeholder="默认同入库仓库"
                  value={woForm.backflushWarehouseId}
                  onChange={(v) => setWoForm((f) => ({ ...f, backflushWarehouseId: v }))}
                  options={(warehouses ?? []).map((w) => ({ value: w.id, label: w.name }))}
                />
              </div>
            )}
          </Space>
          <Space wrap>
            <div>
              <div style={{ marginBottom: 4 }}>计划开始</div>
              <DatePicker
                value={woForm.planStartDate ? dayjs(woForm.planStartDate) : undefined}
                onChange={(d) =>
                  setWoForm((f) => ({
                    ...f,
                    planStartDate: d ? d.format('YYYY-MM-DD') : undefined,
                  }))
                }
              />
            </div>
            <div>
              <div style={{ marginBottom: 4 }}>计划结束</div>
              <DatePicker
                value={woForm.planEndDate ? dayjs(woForm.planEndDate) : undefined}
                onChange={(d) =>
                  setWoForm((f) => ({ ...f, planEndDate: d ? d.format('YYYY-MM-DD') : undefined }))
                }
              />
            </div>
          </Space>
          <div>
            <div style={{ marginBottom: 4 }}>备注</div>
            <Input
              value={woForm.remark}
              onChange={(e) => setWoForm((f) => ({ ...f, remark: e.target.value }))}
            />
          </div>
        </Space>
      </Modal>

      {/* 订单详情抽屉 */}
      <Drawer
        title={`生产订单 ${drawerOrder?.orderNo ?? ''}`}
        open={drawerId != null}
        onClose={() => setDrawerId(null)}
        width={760}
      >
        {drawerOrder && (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <Descriptions
              size="small"
              column={2}
              bordered
              items={[
                { key: 'no', label: '订单号', children: drawerOrder.orderNo },
                {
                  key: 'mat',
                  label: '成品',
                  children: `${drawerOrder.materialCode ?? ''} ${drawerOrder.materialName ?? ''}`,
                },
                { key: 'qty', label: '数量', children: String(drawerOrder.qty) },
                {
                  key: 'bom',
                  label: 'BOM',
                  children: `#${drawerOrder.bomId} ${drawerOrder.bomVersion}`,
                },
                {
                  key: 'plan',
                  label: '计划周期',
                  children: `${drawerOrder.planStartDate ?? '-'} ~ ${drawerOrder.planEndDate ?? '-'}`,
                },
                {
                  key: 'finish',
                  label: '完工(良/不良)',
                  children: `${drawerOrder.finishQty} / ${drawerOrder.badQty}`,
                },
                {
                  key: 'status',
                  label: '状态',
                  children: WO_STATUS[drawerOrder.status]?.label ?? drawerOrder.status,
                },
                {
                  key: 'source',
                  label: '来源',
                  children: drawerOrder.sourceType === 'MRP_SUGGESTION' ? `MRP建议 #${drawerOrder.sourceId}` : '手工',
                },
              ]}
            />
            <Card size="small" title="领料记录">
              <Table<MaterialIssueRow>
                rowKey="id"
                size="small"
                columns={issueColumns}
                dataSource={drawerIssues?.records ?? []}
                pagination={false}
                locale={{ emptyText: '暂无领料记录' }}
              />
            </Card>
          </Space>
        )}
      </Drawer>

      {/* 领料弹窗（两步） */}
      <Modal
        title={`生产领料 —— 订单 #${issueWoId ?? ''}`}
        open={issueOpen}
        onCancel={() => setIssueOpen(false)}
        footer={null}
        width={760}
        destroyOnClose
      >
        {draftIssueId == null ? (
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Text type="secondary">
              领料明细将按订单 BOM 自动带出（应领 = 用量 × 订单数量 × (1+损耗率)），生成草稿后可核对实领数量再审核扣库存。
            </Text>
            <div>
              <div style={{ marginBottom: 4 }}>发料仓库</div>
              <Select
                style={{ width: 260 }}
                placeholder="选择发料仓库"
                value={issueWarehouseId}
                onChange={setIssueWarehouseId}
                options={(warehouses ?? []).map((w) => ({ value: w.id, label: w.name }))}
              />
            </div>
            <Button
              type="primary"
              disabled={!issueWarehouseId}
              loading={createIssueMutation.isPending}
              onClick={() => createIssueMutation.mutate()}
            >
              按BOM生成领料单
            </Button>
          </Space>
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Text>
              领料单 <b>{draftIssue?.issueNo}</b>（仓库 {draftIssue?.warehouseId}），请核对实领数量：
            </Text>
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={draftIssue?.items ?? []}
              columns={[
                { title: '子件编码', dataIndex: 'materialCode', width: 110 },
                { title: '子件名称', dataIndex: 'materialName', ellipsis: true },
                { title: '应领', dataIndex: 'shouldQty', width: 90, align: 'right' },
                {
                  title: '实领',
                  dataIndex: 'actualQty',
                  width: 130,
                  render: (_, item) => (
                    <InputNumber
                      min={0}
                      value={actualQtyMap[item.materialId] ?? item.actualQty}
                      onChange={(v) =>
                        setActualQtyMap((m) => ({ ...m, [item.materialId]: v ?? 0 }))
                      }
                    />
                  ),
                },
              ]}
            />
            <Space>
              <Button
                type="primary"
                loading={auditIssueMutation.isPending}
                onClick={() => auditIssueMutation.mutate()}
              >
                审核扣库存
              </Button>
              <Button danger onClick={() => deleteIssueMutation.mutate(draftIssueId)}>
                删除草稿
              </Button>
            </Space>
          </Space>
        )}
      </Modal>

      {/* 完工入库弹窗 */}
      <Modal
        title={`完工入库 —— 订单 #${finishWoId ?? ''}`}
        open={finishOpen}
        onCancel={() => setFinishOpen(false)}
        onOk={() => {
          if (!finishForm.warehouseId) return message.warning('请选择入库仓库')
          if (finishForm.goodQty + finishForm.badQty <= 0) return message.warning('请录入完工数量')
          finishMutation.mutate()
        }}
        confirmLoading={finishMutation.isPending}
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Space wrap>
            <div>
              <div style={{ marginBottom: 4 }}>良品数量（入库）</div>
              <InputNumber
                min={0}
                style={{ width: 140 }}
                value={finishForm.goodQty}
                onChange={(v) => setFinishForm((f) => ({ ...f, goodQty: v ?? 0 }))}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4 }}>不良品数量（不入库）</div>
              <InputNumber
                min={0}
                style={{ width: 140 }}
                value={finishForm.badQty}
                onChange={(v) => setFinishForm((f) => ({ ...f, badQty: v ?? 0 }))}
              />
            </div>
          </Space>
          <div>
            <div style={{ marginBottom: 4 }}>入库仓库</div>
            <Select
              style={{ width: 260 }}
              placeholder="选择入库仓库"
              value={finishForm.warehouseId}
              onChange={(v) => setFinishForm((f) => ({ ...f, warehouseId: v }))}
              options={(warehouses ?? []).map((w) => ({ value: w.id, label: w.name }))}
            />
          </div>
        </Space>
      </Modal>
    </>
  )
}

// ==================== 委外加工 Tab ====================

function OutsourceTab() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [pageNum, setPageNum] = useState(1)

  const [editorOpen, setEditorOpen] = useState(false)
  const [osForm, setOsForm] = useState<{
    supplierId?: number
    materialId?: number
    qty: number
    issueWarehouseId?: number
    inWarehouseId?: number
    feeUnitPrice: number
    remark: string
  }>({ qty: 10, feeUnitPrice: 0, remark: '' })

  const [receiveOpen, setReceiveOpen] = useState(false)
  const [receiveId, setReceiveId] = useState<number | null>(null)
  const [receiveForm, setReceiveForm] = useState<{ goodQty: number; warehouseId?: number }>({
    goodQty: 0,
  })

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['mrp', 'outsources', keyword, status, pageNum],
    queryFn: () => outsourceApi.page({ pageNum, pageSize: 10, keyword: keyword || undefined, status }),
  })

  const { data: warehouses } = useQuery({
    queryKey: ['mrp', 'warehouses'],
    queryFn: () => mrpApi.warehouses(),
  })

  const { data: suppliers } = useQuery({
    queryKey: ['mrp', 'suppliers'],
    queryFn: () => mrpApi.suppliers(),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['mrp', 'outsources'] })

  const createMutation = useMutation({
    mutationFn: () =>
      outsourceApi.create({
        supplierId: osForm.supplierId as number,
        materialId: osForm.materialId as number,
        qty: osForm.qty,
        issueWarehouseId: osForm.issueWarehouseId,
        inWarehouseId: osForm.inWarehouseId,
        feeUnitPrice: osForm.feeUnitPrice,
        remark: osForm.remark || undefined,
      }),
    onSuccess: () => {
      message.success('委外单已创建（草稿）')
      setEditorOpen(false)
      invalidate()
    },
  })

  const actionMutation = useMutation({
    mutationFn: ({ action, id }: { action: 'issue' | 'delete'; id: number }) =>
      action === 'issue' ? outsourceApi.issueMaterial(id) : outsourceApi.remove(id),
    onSuccess: (_d, vars) => {
      message.success(vars.action === 'issue' ? '委外发料完成，库存已扣减' : '委外单已删除')
      invalidate()
    },
  })

  const receiveMutation = useMutation({
    mutationFn: () =>
      outsourceApi.receive(receiveId as number, {
        goodQty: receiveForm.goodQty,
        warehouseId: receiveForm.warehouseId,
      }),
    onSuccess: () => {
      message.success('完工入库完成，加工费应付已生成')
      setReceiveOpen(false)
      invalidate()
    },
  })

  const columns: ColumnsType<OutsourceRow> = [
    { title: '委外单号', dataIndex: 'orderNo', width: 140 },
    { title: '供应商', dataIndex: 'supplierName', width: 130, ellipsis: true },
    { title: '成品编码', dataIndex: 'materialCode', width: 105 },
    { title: '成品名称', dataIndex: 'materialName', ellipsis: true },
    { title: '数量', dataIndex: 'qty', width: 75, align: 'right' },
    { title: '加工费单价', dataIndex: 'feeUnitPrice', width: 95, align: 'right' },
    { title: 'BOM版本', dataIndex: 'bomVersion', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 85,
      render: (v: string) => {
        const s = OS_STATUS[v]
        return s ? <Tag color={s.color}>{s.label}</Tag> : v
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'op',
      width: 220,
      render: (_, row) => (
        <Space size={4}>
          {row.status === 'DRAFT' && (
            <>
              <Popconfirm
                title="发料将按BOM子件扣减发料仓库存，确认？"
                onConfirm={() => actionMutation.mutate({ action: 'issue', id: row.id })}
              >
                <Button size="small" type="primary" ghost>
                  发料
                </Button>
              </Popconfirm>
              <Popconfirm title="删除草稿委外单？" onConfirm={() => actionMutation.mutate({ action: 'delete', id: row.id })}>
                <Button size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            </>
          )}
          {row.status === 'ISSUED' && (
            <Button
              size="small"
              onClick={() => {
                setReceiveId(row.id)
                setReceiveForm({ goodQty: Number(row.qty), warehouseId: row.inWarehouseId ?? undefined })
                setReceiveOpen(true)
              }}
            >
              完工入库
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Card>
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="成品物料编码 / 名称搜索"
          style={{ width: 230 }}
          allowClear
          onSearch={(v) => {
            setPageNum(1)
            setKeyword(v)
          }}
        />
        <Select
          placeholder="状态筛选"
          allowClear
          style={{ width: 120 }}
          value={status}
          onChange={(v) => {
            setPageNum(1)
            setStatus(v)
          }}
          options={Object.entries(OS_STATUS).map(([value, s]) => ({ value, label: s.label }))}
        />
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setOsForm({ qty: 10, feeUnitPrice: 0, remark: '' })
            setEditorOpen(true)
          }}
        >
          新建委外单
        </Button>
      </Space>

      <Table<OutsourceRow>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading || isFetching}
        pagination={{
          total: data?.total ?? 0,
          current: data?.pageNum ?? 1,
          pageSize: data?.pageSize ?? 10,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p) => setPageNum(p),
        }}
      />

      <Modal
        title="新建委外单"
        open={editorOpen}
        onCancel={() => setEditorOpen(false)}
        onOk={() => {
          if (!osForm.supplierId) return message.warning('请选择供应商')
          if (!osForm.materialId) return message.warning('请选择成品物料')
          createMutation.mutate()
        }}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div>
            <div style={{ marginBottom: 4 }}>供应商</div>
            <Select
              style={{ width: '100%' }}
              showSearch
              optionFilterProp="label"
              placeholder="选择供应商"
              value={osForm.supplierId}
              onChange={(v) => setOsForm((f) => ({ ...f, supplierId: v }))}
              options={(suppliers ?? []).map((s) => ({ value: s.id, label: s.name }))}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4 }}>加工成品（须有已审核 BOM，发料按 BOM 带出）</div>
            <MaterialSelect
              value={osForm.materialId}
              onChange={(v) => setOsForm((f) => ({ ...f, materialId: v }))}
            />
          </div>
          <Col span={24}>
            <Space wrap>
              <div>
                <div style={{ marginBottom: 4 }}>委外数量</div>
                <InputNumber
                  min={0.0001}
                  style={{ width: 120 }}
                  value={osForm.qty}
                  onChange={(v) => setOsForm((f) => ({ ...f, qty: v ?? 1 }))}
                />
              </div>
              <div>
                <div style={{ marginBottom: 4 }}>加工费单价(元/件)</div>
                <InputNumber
                  min={0}
                  precision={2}
                  style={{ width: 130 }}
                  value={osForm.feeUnitPrice}
                  onChange={(v) => setOsForm((f) => ({ ...f, feeUnitPrice: v ?? 0 }))}
                />
              </div>
            </Space>
          </Col>
          <Space wrap>
            <div>
              <div style={{ marginBottom: 4 }}>发料仓库</div>
              <Select
                style={{ width: 180 }}
                allowClear
                placeholder="发料仓库"
                value={osForm.issueWarehouseId}
                onChange={(v) => setOsForm((f) => ({ ...f, issueWarehouseId: v }))}
                options={(warehouses ?? []).map((w) => ({ value: w.id, label: w.name }))}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4 }}>入库仓库</div>
              <Select
                style={{ width: 180 }}
                allowClear
                placeholder="完工入库仓库"
                value={osForm.inWarehouseId}
                onChange={(v) => setOsForm((f) => ({ ...f, inWarehouseId: v }))}
                options={(warehouses ?? []).map((w) => ({ value: w.id, label: w.name }))}
              />
            </div>
          </Space>
          <div>
            <div style={{ marginBottom: 4 }}>备注</div>
            <Input
              value={osForm.remark}
              onChange={(e) => setOsForm((f) => ({ ...f, remark: e.target.value }))}
            />
          </div>
        </Space>
      </Modal>

      <Modal
        title={`委外完工入库 #${receiveId ?? ''}`}
        open={receiveOpen}
        onCancel={() => setReceiveOpen(false)}
        onOk={() => {
          if (receiveForm.goodQty <= 0) return message.warning('请录入入库数量')
          receiveMutation.mutate()
        }}
        confirmLoading={receiveMutation.isPending}
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Text type="secondary">入库将回写成品库存并按 数量×加工费单价 生成应付（fin_payable）。</Text>
          <Space wrap>
            <div>
              <div style={{ marginBottom: 4 }}>入库数量</div>
              <InputNumber
                min={0}
                style={{ width: 140 }}
                value={receiveForm.goodQty}
                onChange={(v) => setReceiveForm((f) => ({ ...f, goodQty: v ?? 0 }))}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4 }}>入库仓库</div>
              <Select
                style={{ width: 200 }}
                placeholder="入库仓库"
                value={receiveForm.warehouseId}
                onChange={(v) => setReceiveForm((f) => ({ ...f, warehouseId: v }))}
                options={(warehouses ?? []).map((w) => ({ value: w.id, label: w.name }))}
              />
            </div>
          </Space>
        </Space>
      </Modal>
    </Card>
  )
}
