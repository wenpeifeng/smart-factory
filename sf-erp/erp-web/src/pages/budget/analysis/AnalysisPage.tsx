import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, Col, Empty, Progress, Row, Select, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
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
import {
  analysisApi,
  optionsApi,
  planApi,
  type AnalysisRow,
  type MonthlyCurvePoint,
} from '@/api/budget'
import { fmtMoney } from '../shared'

/**
 * 预算执行分析页 —— 部门×科目 预算/已用/余额/执行率 + 月度执行曲线。
 * <p>
 * 已用口径：凭证（已过账费用/成本科目借方，按制单人部门）+ 付款/手工登记执行数；
 * 执行率 >100% 标红（Progress exception 语义色）。
 */
export default function AnalysisPage() {
  const [planId, setPlanId] = useState<number | undefined>(undefined)
  const [deptId, setDeptId] = useState<number | undefined>(undefined)
  const [subjectId, setSubjectId] = useState<number | undefined>(undefined)

  // 方案下拉（含草稿，便于编制过程预览；默认选最近一个方案）
  const { data: planPage } = useQuery({
    queryKey: ['budget', 'plans', 'for-analysis'],
    queryFn: () => planApi.page({ pageNum: 1, pageSize: 100 }),
  })
  const plans = planPage?.records ?? []
  const effectivePlanId = planId ?? plans[0]?.id
  const currentPlan = plans.find((p) => p.id === effectivePlanId)

  const { data: options } = useQuery({ queryKey: ['budget', 'options'], queryFn: optionsApi.get })

  const { data: rows, isLoading } = useQuery({
    queryKey: ['budget', 'analysis', effectivePlanId, deptId],
    queryFn: () => analysisApi.rows(effectivePlanId!, deptId),
    enabled: !!effectivePlanId,
  })

  const { data: curve } = useQuery({
    queryKey: ['budget', 'analysis', 'monthly', effectivePlanId, deptId, subjectId],
    queryFn: () => analysisApi.monthly(effectivePlanId!, deptId, subjectId),
    enabled: !!effectivePlanId,
  })

  const deptOptions = (options?.depts ?? []).map((d) => ({
    value: d.id,
    label: d.parentId === 0 ? d.name : `　└ ${d.name}`,
  }))
  const subjectOptions = (options?.subjects ?? []).map((s) => ({
    value: s.id,
    label: `${s.code} ${s.name}`,
  }))
  const planOptions = plans.map((p) => ({
    value: p.id,
    label: `${p.planCode} ${p.planName}(${p.budgetYear})`,
  }))

  /** 曲线图数据（月份文案 + 数值字段） */
  const chartData = (curve ?? []).map((p: MonthlyCurvePoint) => ({
    month: `${p.month}月`,
    预算: p.budgetAmount,
    已用: p.usedAmount,
    rate: p.executionRate,
  }))

  const columns: ColumnsType<AnalysisRow> = [
    { title: '部门', dataIndex: 'deptName', width: 140 },
    { title: '科目编码', dataIndex: 'subjectCode', width: 100 },
    { title: '预算科目', dataIndex: 'subjectName', width: 140 },
    {
      title: '预算金额',
      dataIndex: 'budgetAmount',
      width: 130,
      align: 'right',
      render: (v: number) => fmtMoney(v),
    },
    {
      title: '已用金额',
      dataIndex: 'usedAmount',
      width: 130,
      align: 'right',
      render: (v: number) => fmtMoney(v),
    },
    {
      title: '余额',
      dataIndex: 'balanceAmount',
      width: 130,
      align: 'right',
      render: (v: number) => (
        <span style={{ color: v < 0 ? '#ff4d4f' : undefined }}>{fmtMoney(v)}</span>
      ),
    },
    {
      title: '执行率',
      dataIndex: 'executionRate',
      width: 190,
      render: (rate: number | null) => {
        if (rate === null) return '-'
        const over = rate > 100
        return (
          <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Progress
              percent={Math.min(rate, 100)}
              showInfo={false}
              size="small"
              style={{ width: 90, marginBottom: 0 }}
              status={over ? 'exception' : undefined}
            />
            <span style={{ color: over ? '#ff4d4f' : undefined, fontVariantNumeric: 'tabular-nums' }}>
              {rate.toFixed(2)}%
            </span>
          </span>
        )
      },
    },
  ]

  // 方案级汇总卡片
  const totalBudget = (rows ?? []).reduce((s, r) => s + r.budgetAmount, 0)
  const totalUsed = (rows ?? []).reduce((s, r) => s + r.usedAmount, 0)
  const totalRate = totalBudget > 0 ? ((totalUsed / totalBudget) * 100).toFixed(2) : '-'

  return (
    <div>
      <PageHeader
        title="预算执行分析"
        subtitle="部门×科目的预算执行进度与月度曲线（已用 = 凭证借方 + 付款/登记执行数）"
        extra={
          <>
            <Select
              placeholder="选择预算方案"
              style={{ width: 260 }}
              showSearch
              optionFilterProp="label"
              value={effectivePlanId}
              options={planOptions}
              onChange={setPlanId}
            />
            <Select
              placeholder="全部部门"
              style={{ width: 160 }}
              allowClear
              showSearch
              optionFilterProp="label"
              value={deptId}
              options={deptOptions}
              onChange={setDeptId}
            />
          </>
        }
      />

      {!effectivePlanId ? (
        <Card>
          <Empty description="暂无预算方案，请先在预算编制中建立并下达方案" />
        </Card>
      ) : (
        <>
          <Row gutter={12} style={{ marginBottom: 12 }}>
            <Col span={8}>
              <Card size="small">
                <Typography.Text type="secondary">预算总额</Typography.Text>
                <div style={{ fontSize: 20, fontWeight: 600 }}>{fmtMoney(totalBudget)}</div>
              </Card>
            </Col>
            <Col span={8}>
              <Card size="small">
                <Typography.Text type="secondary">已用金额</Typography.Text>
                <div style={{ fontSize: 20, fontWeight: 600 }}>{fmtMoney(totalUsed)}</div>
              </Card>
            </Col>
            <Col span={8}>
              <Card size="small">
                <Typography.Text type="secondary">整体执行率</Typography.Text>
                <div style={{ fontSize: 20, fontWeight: 600, color: Number(totalRate) > 100 ? '#ff4d4f' : undefined }}>
                  {totalRate === '-' ? '-' : `${totalRate}%`}
                </div>
              </Card>
            </Col>
          </Row>

          <Card title={`执行明细 — ${currentPlan?.planName ?? ''}(${currentPlan?.budgetYear ?? ''})`}>
            <Table<AnalysisRow>
              rowKey={(r) => `${r.deptId}-${r.subjectId}`}
              size="small"
              columns={columns}
              dataSource={rows ?? []}
              loading={isLoading}
              pagination={false}
            />
          </Card>

          <Card
            title="月度执行曲线"
            style={{ marginTop: 12 }}
            extra={
              <Select
                placeholder="全部科目"
                style={{ width: 220 }}
                allowClear
                showSearch
                optionFilterProp="label"
                value={subjectId}
                options={subjectOptions}
                onChange={setSubjectId}
              />
            }
          >
            {chartData.length === 0 ? (
              <Empty description="暂无月度数据" />
            ) : (
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 0, left: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#eef1f5" />
                  <XAxis dataKey="month" stroke="#8c939c" />
                  <YAxis stroke="#8c939c" tickFormatter={(v) => fmtMoney(Number(v))} width={110} />
                  <Tooltip
                    formatter={(value, name) => (name === 'rate' ? [`${value}%`, '执行率'] : [fmtMoney(Number(value)), String(name)])}
                  />
                  <Legend />
                  <Line type="monotone" dataKey="预算" stroke="#1668dc" strokeWidth={2} dot={false} />
                  <Line type="monotone" dataKey="已用" stroke="#52c41a" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            )}
          </Card>
        </>
      )}
    </div>
  )
}
