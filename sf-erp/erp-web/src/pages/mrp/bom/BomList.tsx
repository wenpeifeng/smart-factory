import HasPerm from '@/components/HasPerm'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import {
  bomApi,
  type BomForm,
  type BomItemRow,
  type BomRow,
  type BomTreeNode,
} from '@/api/mrp'
import MaterialSelect from '../components/MaterialSelect'
import SubstituteModal from './SubstituteModal'

const { Title } = Typography

/** BOM 状态标签映射 */
const BOM_STATUS: Record<string, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  ACTIVE: { label: '已审核', color: 'success' },
  DISABLED: { label: '停用', color: 'warning' },
}

/** 编辑态子件行（前端临时 key） */
interface EditableItem {
  key: string
  materialId?: number
  materialName?: string
  qty: number
  lossRate: number
}

/** 树形表格行 */
interface TreeRow {
  key: string
  code: string | null
  name: string
  unit: string | null
  level: number
  qty: number
  lossRate: number
  bomVersion: string | null
  children?: TreeRow[]
}

/** 后端树节点 → AntD Table 树形数据 */
function toTreeRows(node: BomTreeNode, prefix: string): TreeRow {
  const key = `${prefix}/${node.materialId}`
  return {
    key,
    code: node.code,
    name: node.name,
    unit: node.unit,
    level: node.level,
    qty: node.qty,
    lossRate: node.lossRate,
    bomVersion: node.bomVersion,
    children: node.children.length ? node.children.map((c) => toTreeRows(c, key)) : undefined,
  }
}

const TREE_COLUMNS: ColumnsType<TreeRow> = [
  { title: '层级', dataIndex: 'level', width: 70 },
  { title: '物料编码', dataIndex: 'code', width: 130 },
  { title: '物料名称', dataIndex: 'name', ellipsis: true },
  { title: '单位', dataIndex: 'unit', width: 70 },
  { title: '单位用量', dataIndex: 'qty', width: 100, align: 'right' },
  { title: '损耗率%', dataIndex: 'lossRate', width: 90, align: 'right' },
  { title: '展开BOM版本', dataIndex: 'bomVersion', width: 110 },
]

const ITEM_COLUMNS: ColumnsType<BomItemRow> = [
  { title: '子件编码', dataIndex: 'materialCode', width: 130 },
  { title: '子件名称', dataIndex: 'materialName', ellipsis: true },
  { title: '规格', dataIndex: 'spec', width: 130, ellipsis: true },
  { title: '单位', dataIndex: 'unit', width: 70 },
  { title: '单位用量', dataIndex: 'qty', width: 100, align: 'right' },
  { title: '损耗率%', dataIndex: 'lossRate', width: 90, align: 'right' },
]

/**
 * BOM 物料清单页：列表 + 编辑（子件明细）+ 多级树形展开 + 复制新版本/审核/停用。
 */
export default function BomList() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [pageNum, setPageNum] = useState(1)

  // 编辑弹窗状态
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<{ materialId?: number; version: string; remark: string }>({
    version: '',
    remark: '',
  })
  const [items, setItems] = useState<EditableItem[]>([])

  // 替代料维护弹窗
  const [subOpen, setSubOpen] = useState(false)
  const [subBom, setSubBom] = useState<BomRow | null>(null)

  // 树形/复制/详情弹窗
  const [treeOpen, setTreeOpen] = useState(false)
  const [treeBomId, setTreeBomId] = useState<number | null>(null)
  const [copyOpen, setCopyOpen] = useState(false)
  const [copyBomId, setCopyBomId] = useState<number | null>(null)
  const [newVersion, setNewVersion] = useState('')
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailId, setDetailId] = useState<number | null>(null)

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['mrp', 'boms', keyword, status, pageNum],
    queryFn: () => bomApi.page({ pageNum, pageSize: 10, keyword: keyword || undefined, status }),
  })

  const { data: treeData, isLoading: treeLoading } = useQuery({
    queryKey: ['mrp', 'bom-tree', treeBomId],
    queryFn: () => bomApi.tree(treeBomId as number),
    enabled: treeOpen && treeBomId != null,
  })

  const { data: detailData, isLoading: detailLoading } = useQuery({
    queryKey: ['mrp', 'bom-detail', detailId],
    queryFn: () => bomApi.detail(detailId as number),
    enabled: detailOpen && detailId != null,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['mrp', 'boms'] })

  const saveMutation = useMutation({
    mutationFn: async (payload: BomForm) => {
      if (editingId) await bomApi.update(editingId, payload)
      else await bomApi.create(payload)
    },
    onSuccess: () => {
      message.success(editingId ? 'BOM 已保存' : 'BOM 已创建（草稿）')
      setEditorOpen(false)
      invalidate()
    },
  })

  const actionMutation = useMutation({
    mutationFn: async ({ action, id }: { action: 'audit' | 'disable' | 'delete' | 'copy'; id: number }) => {
      if (action === 'audit') await bomApi.audit(id)
      else if (action === 'disable') await bomApi.disable(id)
      else if (action === 'delete') await bomApi.remove(id)
      else await bomApi.copy(id, newVersion)
    },
    onSuccess: (_data, vars) => {
      const tips: Record<string, string> = {
        audit: 'BOM 已审核（有效）',
        disable: 'BOM 已停用',
        delete: 'BOM 已删除',
        copy: '已复制为新版本（草稿）',
      }
      message.success(tips[vars.action])
      if (vars.action === 'copy') setCopyOpen(false)
      invalidate()
    },
  })

  /** 打开新建/编辑弹窗（编辑时先拉详情回填） */
  const openEditor = async (row?: BomRow) => {
    if (row) {
      const detail = await bomApi.detail(row.id)
      setEditingId(row.id)
      setForm({
        materialId: detail.materialId,
        version: detail.version,
        remark: detail.remark ?? '',
      })
      setItems(
        (detail.items ?? []).map((i, idx) => ({
          key: `item-${idx}`,
          materialId: i.materialId,
          materialName: i.materialName ?? undefined,
          qty: i.qty,
          lossRate: i.lossRate,
        })),
      )
    } else {
      setEditingId(null)
      setForm({ version: '', remark: '' })
      setItems([{ key: 'item-0', qty: 1, lossRate: 0 }])
    }
    setEditorOpen(true)
  }

  const submitEditor = () => {
    if (!form.materialId) return message.warning('请选择成品物料')
    if (!form.version.trim()) return message.warning('请填写版本号')
    if (items.length === 0) return message.warning('请至少添加一个子件')
    if (items.some((i) => !i.materialId)) return message.warning('存在未选择物料的子件行')
    saveMutation.mutate({
      materialId: form.materialId,
      version: form.version.trim(),
      remark: form.remark || undefined,
      items: items.map((i) => ({
        materialId: i.materialId as number,
        qty: i.qty,
        lossRate: i.lossRate,
      })),
    })
  }

  const columns: ColumnsType<BomRow> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '成品编码', dataIndex: 'materialCode', width: 130 },
    { title: '成品名称', dataIndex: 'materialName', ellipsis: true },
    { title: '单位', dataIndex: 'unit', width: 70 },
    { title: '版本', dataIndex: 'version', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const s = BOM_STATUS[v]
        return s ? <Tag color={s.color}>{s.label}</Tag> : v
      },
    },
    { title: '子件数', dataIndex: 'itemCount', width: 80, align: 'right' },
    { title: '创建时间', dataIndex: 'createTime', width: 165 },
    {
      title: '操作',
      key: 'actions',
      width: 300,
      render: (_, row) => (
        <Space size={4} wrap>
          <HasPerm code="mrp:bom:edit">
            <Button size="small" onClick={() => openEditor(row)}>
              编辑
            </Button>
          </HasPerm>
          {row.status === 'DRAFT' && (
            <HasPerm code="mrp:bom:audit">
              <Popconfirm
                title="审核后 BOM 生效，可供 MRP/生产订单使用"
                onConfirm={() => actionMutation.mutate({ action: 'audit', id: row.id })}
              >
                <Button size="small" type="primary" ghost>
                  审核
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
          {row.status === 'ACTIVE' && (
            <HasPerm code="mrp:bom:audit">
              <Popconfirm
                title="停用后该 BOM 不再参与 MRP 展开"
                onConfirm={() => actionMutation.mutate({ action: 'disable', id: row.id })}
              >
                <Button size="small">停用</Button>
              </Popconfirm>
            </HasPerm>
          )}
          <Button
            size="small"
            onClick={() => {
              setTreeBomId(row.id)
              setTreeOpen(true)
            }}
          >
            树形展开
          </Button>
          <HasPerm code="mrp:bom:substitute">
            <Button
              size="small"
              onClick={() => {
                setSubBom(row)
                setSubOpen(true)
              }}
            >
              替代料
            </Button>
          </HasPerm>
          <Button
            size="small"
            onClick={() => {
              setDetailId(row.id)
              setDetailOpen(true)
            }}
          >
            详情
          </Button>
          <HasPerm code="mrp:bom:add">
            <Button
              size="small"
              onClick={() => {
                setCopyBomId(row.id)
                setNewVersion('')
                setCopyOpen(true)
              }}
            >
              复制新版
            </Button>
          </HasPerm>
          {(row.status === 'DRAFT' || row.status === 'DISABLED') && (
            <HasPerm code="mrp:bom:delete">
              <Popconfirm
                title="确认删除该 BOM？"
                onConfirm={() => actionMutation.mutate({ action: 'delete', id: row.id })}
              >
                <Button size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            </HasPerm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        BOM 物料清单
      </Title>

      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Input.Search
            placeholder="成品物料编码 / 名称搜索"
            style={{ width: 240 }}
            allowClear
            onSearch={(v) => {
              setPageNum(1)
              setKeyword(v)
            }}
          />
          <Select
            placeholder="状态筛选"
            allowClear
            style={{ width: 140 }}
            value={status}
            onChange={(v) => {
              setPageNum(1)
              setStatus(v)
            }}
            options={[
              { value: 'DRAFT', label: '草稿' },
              { value: 'ACTIVE', label: '已审核' },
              { value: 'DISABLED', label: '停用' },
            ]}
          />
          <HasPerm code="mrp:bom:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor()}>
              新建 BOM
            </Button>
          </HasPerm>
        </Space>

        <Table<BomRow>
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

      {/* 编辑弹窗 */}
      <Modal
        title={editingId ? `编辑 BOM #${editingId}` : '新建 BOM'}
        open={editorOpen}
        onCancel={() => setEditorOpen(false)}
        onOk={submitEditor}
        confirmLoading={saveMutation.isPending}
        width={760}
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
          </Space>
          <div>
            <div style={{ marginBottom: 4 }}>备注</div>
            <Input
              placeholder="备注（可选）"
              value={form.remark}
              onChange={(e) => setForm((f) => ({ ...f, remark: e.target.value }))}
            />
          </div>

          <div style={{ fontWeight: 600 }}>子件明细（单位用量 = 单件成品耗用）</div>
          <Table<EditableItem>
            rowKey="key"
            size="small"
            dataSource={items}
            pagination={false}
            columns={[
              {
                title: '子件物料',
                dataIndex: 'materialId',
                render: (_, row) => (
                  <MaterialSelect
                    value={row.materialId}
                    onChange={(v, m) =>
                      setItems((list) =>
                        list.map((i) =>
                          i.key === row.key
                            ? { ...i, materialId: v, materialName: m?.name }
                            : i,
                        ),
                      )
                    }
                  />
                ),
              },
              {
                title: '单位用量',
                dataIndex: 'qty',
                width: 130,
                render: (_, row) => (
                  <InputNumber
                    min={0.000001}
                    value={row.qty}
                    onChange={(v) =>
                      setItems((list) =>
                        list.map((i) => (i.key === row.key ? { ...i, qty: v ?? 1 } : i)),
                      )
                    }
                  />
                ),
              },
              {
                title: '损耗率%',
                dataIndex: 'lossRate',
                width: 130,
                render: (_, row) => (
                  <InputNumber
                    min={0}
                    max={100}
                    value={row.lossRate}
                    onChange={(v) =>
                      setItems((list) =>
                        list.map((i) => (i.key === row.key ? { ...i, lossRate: v ?? 0 } : i)),
                      )
                    }
                  />
                ),
              },
              {
                title: '操作',
                key: 'op',
                width: 80,
                render: (_, row) => (
                  <Button
                    size="small"
                    danger
                    onClick={() => setItems((list) => list.filter((i) => i.key !== row.key))}
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
            icon={<PlusOutlined />}
            onClick={() =>
              setItems((list) => [
                ...list,
                { key: `item-${Date.now()}`, qty: 1, lossRate: 0 },
              ])
            }
          >
            添加子件
          </Button>
        </Space>
      </Modal>

      {/* 替代料维护弹窗 */}
      <SubstituteModal open={subOpen} bom={subBom} onClose={() => setSubOpen(false)} />

      {/* 树形展开弹窗 */}
      <Modal
        title={`BOM 多级展开 #${treeBomId ?? ''}`}
        open={treeOpen}
        onCancel={() => setTreeOpen(false)}
        footer={null}
        width={900}
      >
        <Table<TreeRow>
          rowKey="key"
          size="small"
          columns={TREE_COLUMNS}
          dataSource={treeData ? [toTreeRows(treeData, 'root')] : []}
          loading={treeLoading}
          pagination={false}
          defaultExpandAllRows
          indentSize={24}
        />
      </Modal>

      {/* 详情弹窗 */}
      <Modal
        title={`BOM 详情 #${detailId ?? ''}`}
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={null}
        width={860}
      >
        {detailData && (
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            <div>
              成品：{detailData.materialCode} | {detailData.materialName}（{detailData.unit ?? '-'}）
              ，版本 {detailData.version}
            </div>
            <Table<BomItemRow>
              rowKey="id"
              size="small"
              columns={ITEM_COLUMNS}
              dataSource={detailData.items ?? []}
              loading={detailLoading}
              pagination={false}
            />
          </Space>
        )}
      </Modal>

      {/* 复制新版本弹窗 */}
      <Modal
        title="复制为新版本"
        open={copyOpen}
        onCancel={() => setCopyOpen(false)}
        onOk={() => {
          if (!newVersion.trim()) return message.warning('请填写新版本号')
          actionMutation.mutate({ action: 'copy', id: copyBomId as number })
        }}
        confirmLoading={actionMutation.isPending}
        destroyOnClose
      >
        <Input
          placeholder="新版本号，如 V2.0"
          value={newVersion}
          onChange={(e) => setNewVersion(e.target.value)}
        />
      </Modal>
    </div>
  )
}
