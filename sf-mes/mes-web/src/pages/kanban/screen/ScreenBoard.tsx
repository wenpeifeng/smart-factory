import { useQuery } from '@tanstack/react-query'
import { Card, Col, Empty, List, Progress, Row, Statistic, Table } from 'antd'
import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { ANDON_TYPE_LABEL, WO_STATUS_LABEL, getScreen } from '@/api/report'

/**
 * 车间电子看板大屏（/kanban/screen）。
 * 深色大屏风格；安灯/WIP/工单进度/产量曲线 30 秒轮询刷新，供车间电视墙展示。
 */
export default function ScreenBoard() {
  const { data, isLoading } = useQuery({
    queryKey: ['report', 'screen'],
    queryFn: getScreen,
    refetchInterval: 30000,
  })

  if (isLoading || !data) {
    return <Empty description="加载中…" style={{ marginTop: 120 }} />
  }

  return (
    <div style={{ padding: 16, minHeight: '100vh', background: '#0a1628' }}>
      <h2 style={{ color: '#4fd1ff', textAlign: 'center', letterSpacing: 4, margin: '0 0 16px' }}>
        车间生产电子看板
      </h2>
      <Row gutter={[16, 16]}>
        <Col span={6}>
          <Card size="small" headStyle={{ background: '#12305c', color: '#4fd1ff', border: 'none' }} bodyStyle={{ background: '#0e2140' }} style={{ background: '#0e2140' }}>
            <Statistic title={<span style={{ color: '#8ab' }}>未关闭安灯</span>} value={data.openAndonCount} valueStyle={{ color: data.openAndonCount > 0 ? '#ff6b6b' : '#4fd1ff' }} />
            <List
              size="small"
              dataSource={data.andons}
              renderItem={(a) => (
                <List.Item style={{ borderColor: '#1d3a63', color: '#cde' }}>
                  {ANDON_TYPE_LABEL[a.type] ?? a.type}：{a.cnt}
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col span={18}>
          <Card size="small" title={<span style={{ color: '#4fd1ff' }}>近 7 天产量</span>} headStyle={{ background: '#12305c', border: 'none' }} bodyStyle={{ background: '#0e2140' }} style={{ background: '#0e2140' }}>
            <ResponsiveContainer width="100%" height={180}>
              <AreaChart data={data.dailyOutput}>
                <defs>
                  <linearGradient id="g" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#36cfc9" stopOpacity={0.8} />
                    <stop offset="100%" stopColor="#36cfc9" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="#1d3a63" />
                <XAxis dataKey="d" stroke="#8ab" />
                <YAxis stroke="#8ab" />
                <Tooltip contentStyle={{ background: '#12305c', border: 'none', color: '#cde' }} />
                <Legend />
                <Area type="monotone" dataKey="goodQty" name="合格" stroke="#36cfc9" fill="url(#g)" />
                <Area type="monotone" dataKey="scrapQty" name="报废" stroke="#ff6b6b" fill="none" />
              </AreaChart>
            </ResponsiveContainer>
          </Card>
        </Col>
        <Col span={12}>
          <Card size="small" title={<span style={{ color: '#4fd1ff' }}>工单进度榜（未完工）</span>} headStyle={{ background: '#12305c', border: 'none' }} bodyStyle={{ background: '#0e2140' }} style={{ background: '#0e2140' }}>
            {data.woProgress.length === 0 ? (
              <Empty description={<span style={{ color: '#8ab' }}>暂无生产中工单</span>} />
            ) : (
              data.woProgress.map((w) => (
                <div key={w.woNo} style={{ marginBottom: 6 }}>
                  <div style={{ color: '#cde', fontSize: 12, display: 'flex', justifyContent: 'space-between' }}>
                    <span>{w.woNo} {w.materialName ?? ''}</span>
                    <span>{w.finishQty}/{w.planQty}</span>
                  </div>
                  <Progress percent={Number(w.progressPct) || 0} size="small" strokeColor="#36cfc9" trailColor="#1d3a63" />
                </div>
              ))
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card size="small" title={<span style={{ color: '#4fd1ff' }}>工序在制 WIP Top</span>} headStyle={{ background: '#12305c', border: 'none' }} bodyStyle={{ background: '#0e2140' }} style={{ background: '#0e2140' }}>
            <Table
              size="small"
              rowKey="operationName"
              pagination={false}
              dataSource={data.wipTop}
              columns={[
                { title: <span style={{ color: '#8ab' }}>工序</span>, dataIndex: 'operationName', render: (v: string) => <span style={{ color: '#cde' }}>{v}</span> },
                { title: <span style={{ color: '#8ab' }}>在制</span>, dataIndex: 'wipQty', align: 'right', render: (v: number) => <span style={{ color: '#4fd1ff' }}>{v}</span> },
              ]}
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card size="small" title={<span style={{ color: '#4fd1ff' }}>工单状态分布</span>} headStyle={{ background: '#12305c', border: 'none' }} bodyStyle={{ background: '#0e2140' }} style={{ background: '#0e2140' }}>
            <List
              size="small"
              dataSource={data.woStatus}
              renderItem={(s) => (
                <List.Item style={{ borderColor: '#1d3a63', color: '#cde' }}>
                  {WO_STATUS_LABEL[s.status] ?? s.status}
                  <span style={{ color: '#4fd1ff', fontWeight: 600 }}>{s.cnt}</span>
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card size="small" title={<span style={{ color: '#4fd1ff' }}>质量汇总</span>} headStyle={{ background: '#12305c', border: 'none' }} bodyStyle={{ background: '#0e2140' }} style={{ background: '#0e2140' }}>
            <List
              size="small"
              dataSource={data.quality}
              renderItem={(q) => (
                <List.Item style={{ borderColor: '#1d3a63', color: '#cde' }}>
                  {q.inspectType}：抽检 {q.sampleQty ?? 0} / 不合格 {q.unqualifiedQty ?? 0}
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
