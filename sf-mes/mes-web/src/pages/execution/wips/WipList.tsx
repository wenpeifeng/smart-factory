import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Input, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import PageHeader from '@/components/PageHeader'
import { getWips, type Wip } from '@/api/execution'

/** 在制品（WIP）查询页：工单 x 工序投入/产出汇总 */
export default function WipList() {
  const [keyword, setKeyword] = useState('')
  const { data, isLoading } = useQuery({
    queryKey: ['execution', 'wips', keyword],
    queryFn: () => getWips({ pageNum: 1, pageSize: 20, keyword: keyword || undefined }),
  })

  const columns: ColumnsType<Wip> = [
    { title: '工单号', dataIndex: 'woNo', width: 160 },
    { title: '工序', dataIndex: 'operationName', render: (_, r) => `${r.seqNo}-${r.operationName}` },
    { title: '投入', dataIndex: 'inputQty', align: 'right' },
    { title: '产出(合格)', dataIndex: 'outputQty', align: 'right' },
    { title: '在制', dataIndex: 'wipQty', align: 'right' },
    { title: '报废', dataIndex: 'scrapQty', align: 'right' },
    { title: '返修', dataIndex: 'reworkQty', align: 'right' },
    { title: '最近过站', dataIndex: 'lastStationTime', width: 170 },
  ]

  return (
    <>
      <PageHeader title="在制品（WIP）" subtitle="工单 x 工序维度的投入/产出/在制汇总，随过站报工实时更新" />
      <Table<Wip>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => <Input.Search placeholder="工单号" allowClear style={{ width: 220 }} onSearch={setKeyword} />}
      />
    </>
  )
}
