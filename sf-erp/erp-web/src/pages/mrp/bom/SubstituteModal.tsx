import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Input, InputNumber, Modal, Space, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import {
  bomApi,
  bomSubstituteApi,
  type BomItemRow,
  type BomRow,
  type BomSubstituteItemForm,
} from '@/api/mrp'
import MaterialSelect from '../components/MaterialSelect'

const { Text } = Typography

/** 编辑态替代料行（前端临时 key） */
interface EditableSub {
  key: string
  subMaterialId?: number
  priority: number
  ratio: number
  remark: string
}

/**
 * BOM 替代料维护弹窗（V621）：
 * 左侧 BOM 子件（主料）列表，逐子件维护替代组（替代料 + 优先级 + 换算比），
 * 按子件行整组保存。仅草稿 BOM 可维护（后端强校验）。
 */
export default function SubstituteModal({
  open,
  bom,
  onClose,
}: {
  open: boolean
  bom: BomRow | null
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [subsByItem, setSubsByItem] = useState<Record<number, EditableSub[]>>({})
  const [savingItem, setSavingItem] = useState<number | null>(null)

  const { data: detail, isLoading } = useQuery({
    queryKey: ['mrp', 'bom-detail', bom?.id],
    queryFn: () => bomApi.detail(bom?.id as number),
    enabled: open && bom != null,
  })

  const { data: subMap, isLoading: subLoading } = useQuery({
    queryKey: ['mrp', 'bom-substitutes', bom?.id],
    queryFn: () => bomSubstituteApi.listByBom(bom?.id as number),
    enabled: open && bom != null,
  })

  // 替代组拉取后初始化编辑态
  useEffect(() => {
    if (!subMap || !detail) return
    const next: Record<number, EditableSub[]> = {}
    for (const item of detail.items ?? []) {
      next[item.id] = (subMap[String(item.id)] ?? []).map((s, idx) => ({
        key: `sub-${item.id}-${idx}`,
        subMaterialId: s.subMaterialId,
        priority: s.priority,
        ratio: s.ratio,
        remark: s.remark ?? '',
      }))
    }
    setSubsByItem(next)
  }, [subMap, detail])

  const saveMutation = useMutation({
    mutationFn: async ({ bomItemId, items }: { bomItemId: number; items: BomSubstituteItemForm[] }) => {
      await bomSubstituteApi.saveBatch(bomItemId, items)
    },
    onSuccess: (_d, vars) => {
      message.success(`子件 #${vars.bomItemId} 替代料已保存`)
      setSavingItem(null)
      queryClient.invalidateQueries({ queryKey: ['mrp', 'bom-substitutes', bom?.id] })
    },
  })

  const setItemSubs = (bomItemId: number, updater: (rows: EditableSub[]) => EditableSub[]) =>
    setSubsByItem((prev) => ({
      ...prev,
      [bomItemId]: updater(prev[bomItemId] ?? []),
    }))

  const submitItem = (bomItemId: number) => {
    const rows = subsByItem[bomItemId] ?? []
    if (rows.some((r) => !r.subMaterialId)) return message.warning('存在未选择物料的替代料行')
    if (rows.some((r) => !r.priority || r.priority < 1)) return message.warning('优先级必须≥1')
    setSavingItem(bomItemId)
    saveMutation.mutate({
      bomItemId,
      items: rows.map((r) => ({
        subMaterialId: r.subMaterialId as number,
        priority: r.priority,
        ratio: r.ratio,
        remark: r.remark || undefined,
      })),
    })
  }

  const columns: ColumnsType<BomItemRow & { substitutes: EditableSub[] }> = [
    { title: '子件ID', dataIndex: 'id', width: 70 },
    { title: '主料编码', dataIndex: 'materialCode', width: 120 },
    { title: '主料名称', dataIndex: 'materialName', ellipsis: true },
    { title: '单位用量', dataIndex: 'qty', width: 90, align: 'right' },
    {
      title: '替代料数',
      key: 'subCount',
      width: 90,
      align: 'right',
      render: (_, row) => <Tag color={row.substitutes.length ? 'blue' : 'default'}>{row.substitutes.length}</Tag>,
    },
  ]

  return (
    <Modal
      title={`替代料维护 —— BOM #${bom?.id ?? ''}（${bom?.materialCode ?? ''} / ${bom?.materialName ?? ''} ${bom?.version ?? ''}）`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={980}
      destroyOnClose
    >
      <Space direction="vertical" style={{ width: '100%' }} size={8}>
        <Text type="secondary">
          主料 = BOM 子件行自身；主料库存不足时按优先级（1 最高）依次用替代料补足，
          换算比 = 1 单位主料对应替代料数量。倒冲领料链路生效；仅草稿状态 BOM 可维护。
        </Text>
        <Table
          rowKey="id"
          size="small"
          loading={isLoading || subLoading}
          columns={columns}
          dataSource={(detail?.items ?? []).map((i) => ({
            ...i,
            substitutes: subsByItem[i.id] ?? [],
          }))}
          pagination={false}
          expandable={{
            defaultExpandAllRows: true,
            expandedRowRender: (row) => (
              <Space direction="vertical" style={{ width: '100%' }} size={8}>
                <Table<EditableSub>
                  rowKey="key"
                  size="small"
                  pagination={false}
                  dataSource={row.substitutes}
                  columns={[
                    {
                      title: '替代料',
                      dataIndex: 'subMaterialId',
                      render: (_, sub) => (
                        <MaterialSelect
                          value={sub.subMaterialId}
                          onChange={(v) =>
                            setItemSubs(row.id, (rows) =>
                              rows.map((r) => (r.key === sub.key ? { ...r, subMaterialId: v } : r)),
                            )
                          }
                        />
                      ),
                    },
                    {
                      title: '优先级',
                      dataIndex: 'priority',
                      width: 110,
                      render: (_, sub) => (
                        <InputNumber
                          min={1}
                          max={999}
                          value={sub.priority}
                          onChange={(v) =>
                            setItemSubs(row.id, (rows) =>
                              rows.map((r) => (r.key === sub.key ? { ...r, priority: v ?? 1 } : r)),
                            )
                          }
                        />
                      ),
                    },
                    {
                      title: '换算比',
                      dataIndex: 'ratio',
                      width: 120,
                      render: (_, sub) => (
                        <InputNumber
                          min={0.000001}
                          step={0.1}
                          value={sub.ratio}
                          onChange={(v) =>
                            setItemSubs(row.id, (rows) =>
                              rows.map((r) => (r.key === sub.key ? { ...r, ratio: v ?? 1 } : r)),
                            )
                          }
                        />
                      ),
                    },
                    {
                      title: '备注',
                      dataIndex: 'remark',
                      render: (_, sub) => (
                        <Input
                          value={sub.remark}
                          onChange={(e) =>
                            setItemSubs(row.id, (rows) =>
                              rows.map((r) => (r.key === sub.key ? { ...r, remark: e.target.value } : r)),
                            )
                          }
                        />
                      ),
                    },
                    {
                      title: '操作',
                      key: 'op',
                      width: 80,
                      render: (_, sub) => (
                        <Button
                          size="small"
                          danger
                          onClick={() =>
                            setItemSubs(row.id, (rows) => rows.filter((r) => r.key !== sub.key))
                          }
                        >
                          移除
                        </Button>
                      ),
                    },
                  ]}
                />
                <Space>
                  <Button
                    size="small"
                    type="dashed"
                    icon={<PlusOutlined />}
                    onClick={() =>
                      setItemSubs(row.id, (rows) => [
                        ...rows,
                        {
                          key: `sub-${row.id}-${Date.now()}`,
                          priority: rows.length + 1,
                          ratio: 1,
                          remark: '',
                        },
                      ])
                    }
                  >
                    添加替代料
                  </Button>
                  <Button
                    size="small"
                    type="primary"
                    ghost
                    loading={savingItem === row.id}
                    onClick={() => submitItem(row.id)}
                  >
                    保存该子件替代组
                  </Button>
                </Space>
              </Space>
            ),
          }}
        />
      </Space>
    </Modal>
  )
}
