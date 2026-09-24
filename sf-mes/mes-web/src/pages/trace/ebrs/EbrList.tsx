import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Drawer, Input, Modal, Select, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { FileDoneOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import HasPerm from '@/components/HasPerm'
import { generateEbr, getEbrDetail, getEbrs, type TraceEbr } from '@/api/trace'
import { getWorkorders } from '@/api/plan'

/** 电子批记录页：为工单生成快照 + 查看 JSON 全文 */
export default function EbrList() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [woId, setWoId] = useState<number | null>(null)
  const [detailId, setDetailId] = useState<number | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['trace', 'ebrs', keyword],
    queryFn: () => getEbrs({ pageNum: 1, pageSize: 20, keyword: keyword || undefined }),
  })
  const { data: workorders } = useQuery({
    queryKey: ['plan', 'workorders', 'for-ebr'],
    queryFn: () => getWorkorders({ pageNum: 1, pageSize: 100 }),
  })
  const { data: detail } = useQuery({
    queryKey: ['trace', 'ebr', detailId],
    queryFn: () => getEbrDetail(detailId!),
    enabled: detailId != null,
  })

  const genMutation = useMutation({
    mutationFn: (id: number) => generateEbr(id),
    onSuccess: () => {
      message.success('批记录已生成')
      setModalOpen(false)
      queryClient.invalidateQueries({ queryKey: ['trace', 'ebrs'] })
    },
  })

  const columns: ColumnsType<TraceEbr> = [
    { title: '批记录号', dataIndex: 'ebrNo', width: 180 },
    { title: '工单号', dataIndex: 'woNo', width: 160 },
    { title: '产出批次', dataIndex: 'batchNo', width: 180, render: (v) => v ?? '-' },
    { title: '生成时间', dataIndex: 'generateTime', width: 180 },
    { title: '操作', render: (_, r) => <Button size="small" type="link" onClick={() => setDetailId(r.id)}>查看 JSON</Button> },
  ]

  return (
    <>
      <PageHeader
        title="电子批记录"
        subtitle="工单完工数据的合规快照（工艺/过站/用料/质检聚合 JSON，生成后只读）"
        extra={
          <HasPerm code="trace:ebr:generate">
            <Button type="primary" icon={<FileDoneOutlined />} onClick={() => setModalOpen(true)}>生成批记录</Button>
          </HasPerm>
        }
      />
      <Table<TraceEbr>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => <Input.Search placeholder="批记录号/工单号/批次号" allowClear style={{ width: 260 }} onSearch={setKeyword} />}
      />

      <Modal title="选择工单生成批记录" open={modalOpen} onCancel={() => setModalOpen(false)} onOk={() => woId && genMutation.mutate(woId)} confirmLoading={genMutation.isPending}>
        <Select
          showSearch
          optionFilterProp="label"
          placeholder="选择工单"
          style={{ width: '100%' }}
          onChange={(v) => setWoId(v)}
          options={(workorders?.records ?? []).map((w) => ({ value: w.id, label: `${w.woNo}（${w.materialName ?? ''}）` }))}
        />
      </Modal>

      <Drawer title={`批记录：${detail?.ebrNo ?? ''}`} width={720} open={detailId != null} onClose={() => setDetailId(null)}>
        <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', fontSize: 12 }}>{detail?.content}</pre>
      </Drawer>
    </>
  )
}
