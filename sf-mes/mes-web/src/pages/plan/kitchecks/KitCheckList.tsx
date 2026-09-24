import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Drawer, Input, Modal, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { FileSearchOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import { getKitChecks, getLatestKitCheck, getWorkorders, runKitCheck, type KitCheck, type KitCheckItem } from '@/api/plan'

/** 齐套分析页：记录列表 + 对工单执行分析 + 最近结果明细 */
export default function KitCheckList() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [result, setResult] = useState<string | undefined>()
  const [modalOpen, setModalOpen] = useState(false)
  const [woId, setWoId] = useState<number | null>(null)
  const [detailWoId, setDetailWoId] = useState<number | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['plan', 'kitchecks', keyword, result],
    queryFn: () => getKitChecks({ pageNum: 1, pageSize: 20, keyword: keyword || undefined, result }),
  })
  const { data: releasedWos } = useQuery({
    queryKey: ['plan', 'workorders', 'for-kit', 'RELEASED'],
    queryFn: () => getWorkorders({ pageNum: 1, pageSize: 100, status: 'RELEASED' }),
  })
  const { data: latest } = useQuery({
    queryKey: ['plan', 'kitcheck', 'latest', detailWoId],
    queryFn: () => getLatestKitCheck(detailWoId!),
    enabled: detailWoId != null,
  })

  const runMutation = useMutation({
    mutationFn: (id: number) => runKitCheck(id),
    onSuccess: () => {
      message.success('齐套分析完成')
      setModalOpen(false)
      queryClient.invalidateQueries({ queryKey: ['plan', 'kitchecks'] })
    },
  })

  const columns: ColumnsType<KitCheck> = [
    { title: '分析单号', dataIndex: 'checkNo', width: 170 },
    { title: '工单号', dataIndex: 'woNo', width: 160 },
    {
      title: '结果',
      dataIndex: 'result',
      width: 110,
      render: (v: string) => <StatusTag value={v === 'FULL' ? '齐套' : v === 'PART' ? '部分齐套' : '缺料'} semantic={v === 'FULL' ? 'success' : v === 'PART' ? 'warning' : 'error'} />,
    },
    { title: '缺料行数', dataIndex: 'shortCount', width: 100, align: 'right' },
    { title: '分析时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      render: (_, r) => (
        <Button size="small" type="link" onClick={() => setDetailWoId(r.workorderId)}>查看明细</Button>
      ),
    },
  ]

  const itemColumns: ColumnsType<KitCheckItem> = [
    { title: '物料', render: (_, r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}` },
    { title: '未领需求', dataIndex: 'reqQty', align: 'right' },
    { title: '线边可用', dataIndex: 'availableQty', align: 'right' },
    { title: '缺口', dataIndex: 'shortQty', align: 'right' },
    {
      title: '结果',
      dataIndex: 'result',
      width: 90,
      render: (v: string) => <StatusTag value={v === 'OK' ? '齐套' : '缺料'} semantic={v === 'OK' ? 'success' : 'error'} />,
    },
  ]

  return (
    <>
      <PageHeader
        title="齐套分析"
        subtitle="按工单用料需求对比线边仓可用量，生成齐套快照（FULL 齐套 / PART 部分 / SHORT 缺料）"
        extra={
          <HasPerm code="plan:kitcheck:run">
            <Button type="primary" icon={<FileSearchOutlined />} onClick={() => setModalOpen(true)}>执行齐套分析</Button>
          </HasPerm>
        }
      />
      <Table<KitCheck>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Space>
            <Input.Search placeholder="工单号/分析单号" allowClear style={{ width: 220 }} onSearch={setKeyword} />
            <Select
              allowClear
              placeholder="结果"
              style={{ width: 120 }}
              onChange={setResult}
              options={[{ value: 'FULL', label: '齐套' }, { value: 'PART', label: '部分齐套' }, { value: 'SHORT', label: '缺料' }]}
            />
          </Space>
        )}
      />

      <Modal
        title="选择工单执行齐套分析"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => woId && runMutation.mutate(woId)}
        confirmLoading={runMutation.isPending}
      >
        <Select
          showSearch
          optionFilterProp="label"
          placeholder="选择已下达的工单"
          style={{ width: '100%' }}
          onChange={(v) => setWoId(v)}
          options={(releasedWos?.records ?? []).map((w) => ({ value: w.id, label: `${w.woNo}（${w.materialName ?? ''}）` }))}
        />
      </Modal>

      <Drawer title={`齐套明细：${latest?.checkNo ?? ''}`} width={640} open={detailWoId != null} onClose={() => setDetailWoId(null)}>
        <Table rowKey="id" size="small" columns={itemColumns} dataSource={latest?.items ?? []} pagination={false} loading={detailWoId != null && !latest} />
      </Drawer>
    </>
  )
}
