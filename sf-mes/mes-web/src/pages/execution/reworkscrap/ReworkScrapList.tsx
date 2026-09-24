import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Input, Popconfirm, Select, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import HasPerm from '@/components/HasPerm'
import { getReworkScraps, handleReworkScrap, type ReworkScrap } from '@/api/execution'

const TYPE_LABEL: Record<string, string> = { REWORK: '返工', SCRAP: '报废' }

/** 返工/报废单页：列表 + 处理闭环（创建入口在报工/质检环节） */
export default function ReworkScrapList() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [type, setType] = useState<string | undefined>()
  const [status, setStatus] = useState<string | undefined>()

  const { data, isLoading } = useQuery({
    queryKey: ['execution', 'reworkscrap', keyword, type, status],
    queryFn: () => getReworkScraps({ pageNum: 1, pageSize: 20, keyword: keyword || undefined, type, status }),
  })

  const handleMutation = useMutation({
    mutationFn: (id: number) => handleReworkScrap(id, '已处理'),
    onSuccess: () => {
      message.success('处理完成')
      queryClient.invalidateQueries({ queryKey: ['execution', 'reworkscrap'] })
    },
  })

  const columns: ColumnsType<ReworkScrap> = [
    { title: '单号', dataIndex: 'orderNo', width: 160 },
    { title: '类型', dataIndex: 'type', width: 80, render: (v: string) => TYPE_LABEL[v] ?? v },
    { title: '工单号', dataIndex: 'woNo', width: 150 },
    { title: '工序', dataIndex: 'operationName' },
    { title: '数量', dataIndex: 'qty', width: 90, align: 'right' },
    { title: '原因', dataIndex: 'reason', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => <StatusTag value={v === 'CLOSED' ? '已处理' : '待处理'} semantic={v === 'CLOSED' ? 'success' : 'warning'} />,
    },
    {
      title: '操作',
      width: 100,
      render: (_, r) =>
        r.status === 'OPEN' && (
          <HasPerm code="execution:reworkscrap:handle">
            <Popconfirm title="确认处理完成？" onConfirm={() => handleMutation.mutate(r.id)}>
              <Button size="small" type="link">处理</Button>
            </Popconfirm>
          </HasPerm>
        ),
    },
  ]

  return (
    <>
      <PageHeader title="返工 / 报废" subtitle="返工单(FG)/报废单(BF)：过站不合格与质检不合格处置的执行跟踪" />
      <Table<ReworkScrap>
        rowKey="id"
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading}
        pagination={{ total: data?.total ?? 0, pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        title={() => (
          <Space>
            <Input.Search placeholder="单号/工单号" allowClear style={{ width: 200 }} onSearch={setKeyword} />
            <Select allowClear placeholder="类型" style={{ width: 100 }} onChange={setType} options={[{ value: 'REWORK', label: '返工' }, { value: 'SCRAP', label: '报废' }]} />
            <Select allowClear placeholder="状态" style={{ width: 100 }} onChange={setStatus} options={[{ value: 'OPEN', label: '待处理' }, { value: 'CLOSED', label: '已处理' }]} />
          </Space>
        )}
      />
    </>
  )
}
