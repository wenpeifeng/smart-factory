import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, Empty, Input, Space, Table, Tabs } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import PageHeader from '@/components/PageHeader'
import { traceBackward, traceForward, type TraceNode } from '@/api/trace'

/** 正反向追溯页：输入批次/SN/工单号一键双向查询 */
export default function TraceSearch() {
  const [no, setNo] = useState('')
  const [active, setActive] = useState('backward')
  const enabled = no.trim().length > 0

  const backward = useQuery({
    queryKey: ['trace', 'backward', no],
    queryFn: () => traceBackward(no.trim()),
    enabled: enabled && active === 'backward',
  })
  const forward = useQuery({
    queryKey: ['trace', 'forward', no],
    queryFn: () => traceForward(no.trim()),
    enabled: enabled && active === 'forward',
  })

  const columns = (dir: 'backward' | 'forward'): ColumnsType<TraceNode> => [
    { title: dir === 'backward' ? '来料批次/关键件SN' : '下游产品', dataIndex: 'targetNo' },
    { title: '类型', dataIndex: 'targetType', width: 80, render: (v: string) => (v === 'SN' ? 'SN' : v === 'BATCH' ? '批次' : '工单') },
    { title: '工单号', dataIndex: 'woNo', width: 150, render: (v) => v ?? '-' },
    { title: '数量', dataIndex: 'qty', width: 100, align: 'right' },
    { title: '关联时间', dataIndex: 'linkTime', width: 170 },
  ]

  return (
    <>
      <PageHeader title="正反向追溯" subtitle="反向：产品批次/SN/工单号 → 来料批次与关键件；正向：来料批次/关键件SN → 下游产品" />
      <Card>
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Input.Search
            size="large"
            placeholder="输入批次号 / SN / 工单号，选择页签方向后回车查询"
            enterButton="追溯"
            value={no}
            onChange={(e) => setNo(e.target.value)}
            onSearch={() => undefined}
          />
          <Tabs
            activeKey={active}
            onChange={setActive}
            items={[
              {
                key: 'backward',
                label: '反向追溯（产品 → 来料）',
                children: !enabled ? (
                  <Empty description="请输入编号" />
                ) : (
                  <Table rowKey="id" size="small" columns={columns('backward')} dataSource={backward.data ?? []} loading={backward.isLoading} pagination={false} />
                ),
              },
              {
                key: 'forward',
                label: '正向追溯（来料 → 产品）',
                children: !enabled ? (
                  <Empty description="请输入编号" />
                ) : (
                  <Table rowKey="id" size="small" columns={columns('forward')} dataSource={forward.data ?? []} loading={forward.isLoading} pagination={false} />
                ),
              },
            ]}
          />
        </Space>
      </Card>
    </>
  )
}
