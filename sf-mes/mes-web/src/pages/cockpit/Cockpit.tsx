import { useQuery } from '@tanstack/react-query'
import { Card, Col, Empty, List, Row, Statistic } from 'antd'
import { AlertOutlined, ApiOutlined, FileSearchOutlined } from '@ant-design/icons'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import PageHeader from '@/components/PageHeader'
import { WO_STATUS_LABEL, getCockpit } from '@/api/report'

const INSPECT_LABEL: Record<string, string> = { IQC: '来料', IPQC: '过程', FQC: '成品' }

/** 管理驾驶舱：工单/产量/质量/异常核心指标一屏 */
export default function Cockpit() {
  const { data, isLoading } = useQuery({ queryKey: ['report', 'cockpit'], queryFn: getCockpit, refetchInterval: 60000 })
  const totalWo = (data?.woStatus ?? []).reduce((s, x) => s + x.cnt, 0)
  const producing = data?.woStatus?.find((s) => s.status === 'PRODUCING')?.cnt ?? 0

  return (
    <>
      <PageHeader title="管理驾驶舱" subtitle="工厂运行核心指标（60 秒刷新）" />
      {isLoading || !data ? (
        <Empty description="加载中…" />
      ) : (
        <Row gutter={[16, 16]}>
          <Col span={6}>
            <Card><Statistic title="工单总数" value={totalWo} /></Card>
          </Col>
          <Col span={6}>
            <Card><Statistic title="生产中" value={producing} valueStyle={{ color: '#1668dc' }} /></Card>
          </Col>
          <Col span={4}>
            <Card>
              <Statistic
                title={<span><FileSearchOutlined /> 缺料工单</span>}
                value={data.shortWorkorders}
                valueStyle={{ color: data.shortWorkorders > 0 ? '#faad14' : undefined }}
              />
            </Card>
          </Col>
          <Col span={4}>
            <Card>
              <Statistic
                title={<span><AlertOutlined /> 未关闭安灯</span>}
                value={data.openAndons}
                valueStyle={{ color: data.openAndons > 0 ? '#ff4d4f' : undefined }}
              />
            </Card>
          </Col>
          <Col span={4}>
            <Card>
              <Statistic
                title={<span><ApiOutlined /> 回传积压</span>}
                value={data.pendingPushes}
                valueStyle={{ color: data.pendingPushes > 0 ? '#faad14' : undefined }}
              />
            </Card>
          </Col>
          <Col span={14}>
            <Card title="近 14 天产量趋势">
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={data.dailyOutput}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="d" />
                  <YAxis />
                  <Tooltip />
                  <Legend />
                  <Line type="monotone" dataKey="goodQty" name="合格" stroke="#1668dc" />
                  <Line type="monotone" dataKey="scrapQty" name="报废" stroke="#ff4d4f" />
                </LineChart>
              </ResponsiveContainer>
            </Card>
          </Col>
          <Col span={10}>
            <Card title="工单状态分布">
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={data.woStatus.map((s) => ({ ...s, label: WO_STATUS_LABEL[s.status] ?? s.status }))}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="label" />
                  <YAxis />
                  <Tooltip />
                  <Bar dataKey="cnt" name="数量" fill="#1668dc" />
                </BarChart>
              </ResponsiveContainer>
            </Card>
          </Col>
          <Col span={24}>
            <Card title="质量汇总（已提交检验单）">
              <List
                grid={{ column: 3 }}
                dataSource={data.quality}
                renderItem={(q) => {
                  const rate = q.sampleQty ? ((Number(q.qualifiedQty ?? 0) / Number(q.sampleQty)) * 100).toFixed(1) : '-'
                  return (
                    <Card.Grid style={{ width: '33.33%', padding: 16 }}>
                      <Statistic
                        title={`${INSPECT_LABEL[q.inspectType] ?? q.inspectType} 合格率`}
                        value={rate === '-' ? '-' : `${rate}%`}
                        valueStyle={{ color: Number(rate) >= 95 ? '#52c41a' : '#faad14' }}
                      />
                      <div style={{ color: '#999', fontSize: 12 }}>抽检 {q.sampleQty ?? 0} · 不合格 {q.unqualifiedQty ?? 0}</div>
                    </Card.Grid>
                  )
                }}
              />
            </Card>
          </Col>
        </Row>
      )}
    </>
  )
}
