import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Col, Progress, Row, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { ReloadOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import { calcOee, type Oee } from '@/api/equipment'

const pct = (v: number) => `${(v * 100).toFixed(1)}%`

/** OEE 分析页（工作中心维度） */
export default function OeePage() {
  const [refresh, setRefresh] = useState(0)
  const { data, isLoading } = useQuery({
    queryKey: ['equipment', 'oee', refresh],
    queryFn: () => calcOee({}),
  })

  const columns: ColumnsType<Oee> = [
    { title: '工作中心', dataIndex: 'workcenterName', render: (v, r) => v ?? r.workcenterId ?? '-' },
    { title: '负荷(分)', dataIndex: 'plannedMinutes', align: 'right' },
    { title: '运转(分)', dataIndex: 'runMinutes', align: 'right' },
    { title: '时间开动率', dataIndex: 'availability', align: 'right', render: pct },
    { title: '性能开动率', dataIndex: 'performance', align: 'right', render: pct },
    { title: '合格品率', dataIndex: 'quality', align: 'right', render: pct },
    { title: 'OEE', dataIndex: 'oee', align: 'right', render: (v: number) => <StatusTag value={pct(v)} semantic={v >= 0.85 ? 'success' : v >= 0.6 ? 'processing' : 'error'} /> },
  ]

  return (
    <>
      <PageHeader
        title="OEE 分析"
        subtitle="OEE = 时间开动率 x 性能开动率 x 合格品率（近 30 天，工作中心维度；负荷时间=工作中心日产能工时）"
        extra={<Button icon={<ReloadOutlined />} onClick={() => setRefresh((v) => v + 1)}>刷新</Button>}
      />
      <Row gutter={16} style={{ marginBottom: 16 }}>
        {(data ?? []).slice(0, 4).map((o) => (
          <Col key={o.workcenterId ?? 0} span={6}>
            <Card size="small">
              <div style={{ marginBottom: 4 }}>{o.workcenterName ?? '未分配中心'}</div>
              <Progress type="circle" percent={Math.round(o.oee * 100)} size={90} strokeColor={o.oee >= 0.85 ? '#52c41a' : o.oee >= 0.6 ? '#1668dc' : '#ff4d4f'} />
            </Card>
          </Col>
        ))}
      </Row>
      <Table<Oee>
        rowKey={(r) => String(r.workcenterId ?? 0)}
        columns={columns}
        dataSource={data ?? []}
        loading={isLoading}
        pagination={false}
        style={{ background: '#fff', padding: 12, borderRadius: 8 }}
        locale={{ emptyText: '暂无过站数据（先在执行模块做过站报工）' }}
      />
    </>
  )
}
